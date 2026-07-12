# Cairn: Security and Access

**Doc 3 of 4**
**Status:** Draft v2
**Changes in v2:** the permission resolution algorithm now carries a complexity note and an explicit bound; the threat model gains resource-exhaustion entries tied to the new packfile and delta work (decompression and delta-chain limits); a caching-versus-recompute tradeoff is stated for permission lookup.

The permission model is the heart of this document and the part of Cairn that maps onto access-control design and adversarial thinking. The threat model in section 6 reads the system the way a red-teamer would: assets, boundaries, and the ways access could be subverted.

---

## 1. Overview

Cairn has three security jobs:
1. **Authentication:** prove who a principal is (web sessions, access tokens, SSH keys).
2. **Authorization:** decide what that principal may do on a resource (the permission model).
3. **Protocol and data safety:** ensure Git-over-HTTP, Git-over-SSH, and stored data cannot be used to bypass the first two, and cannot be used to exhaust the server.

## 2. Authentication

### 2.1 Principals
A **principal** is the acting identity on a request: a **user** (session or token), an **anonymous reader** (no credentials, may still read public resources), or a scoped **automation identity** (treated as a restricted user).

### 2.2 Web sessions
- Login exchanges credentials for a server-side session; the cookie carries only a high-entropy session id.
- Cookies are `HttpOnly`, `Secure`, `SameSite=Lax`. State-changing requests carry a CSRF token.
- Passwords use a memory-hard hash (argon2id) with a per-user salt. Never plaintext, never a fast hash.

### 2.3 Personal access tokens
- Tokens carry **scopes** (`repo:read`, `repo:write`, `org:admin`) so a leak grants only what the token was minted for.
- Only a hash of the token is stored; the plaintext is shown once at creation.
- Tokens have an expiry and can be revoked immediately.

### 2.4 SSH keys (Git over SSH)
- A user registers a public key; on connect the server authenticates it against the registered set and maps it to that user.
- Fingerprints are recorded; a removed key stops working immediately.

## 3. The permission model (core)

Specified as an algorithm, not prose, because this is what a reviewer studies.

### 3.1 Resources and visibility
Repositories carry a **visibility**: **public** (readable by anyone, including anonymous), **internal** (any authenticated member), **private** (only principals with an explicit grant).

### 3.2 Roles (ordered, least to most)
`read < triage < write < maintain < admin`
- **read:** clone, browse, open issues, comment.
- **triage:** manage issues and PRs without code write.
- **write:** push to unprotected branches, merge where allowed.
- **maintain:** manage settings short of destructive actions.
- **admin:** full control, including access management and deletion.

The total order is what lets resolution be a simple maximum.

### 3.3 Grant sources
A principal can gain a role through more than one path:
1. **Ownership** (implies admin).
2. **Direct collaborator grant.**
3. **Team grant**, inherited down the nested team hierarchy (the Composite structure from Doc 2).
4. **Visibility floor:** public and internal grant an implicit read to the appropriate audience.

### 3.4 Resolution algorithm
```
effective_role(principal, repo):
    if principal is anonymous:
        return read if repo.visibility == public else none

    roles = empty set
    if principal owns repo or is admin of repo.owner_org:
        roles.add(admin)
    if direct grant g exists for (principal, repo):
        roles.add(g.role)
    for team in teams_of(principal):            # walks nested teams
        if team has grant t on repo:
            roles.add(t.role)
    if repo.visibility in {public, internal} and principal is eligible:
        roles.add(read)
    return max(roles) or none                    # total order makes max well-defined
```

**Complexity and bound.** Cost is O(D + T): D direct grants (usually O(1)) plus T edges walked in the nested team hierarchy. The team walk is bounded by the principal's team memberships and the hierarchy depth, and must be explicitly bounded (a depth cap and a visited set) so a pathological or cyclic team structure cannot turn a permission check into an unbounded traversal. See the privilege-confusion and DoS rows in section 6.

**Caching tradeoff.** Resolution can be cached per (principal, repo) for O(1) amortized lookups. The tradeoff is invalidation: any change to a direct grant, a team grant, team membership, or org ownership must invalidate the affected cache entries. The v1 default is to recompute (simple and always correct); add caching only with a clear invalidation story.

**Precedence rule.** Effective role is the maximum across all sources; no path can push a principal below a role another path granted. Explicit deny is deliberately not modeled in v1 to keep the algebra a clean maximum. If added later it becomes a masking pass after the maximum, which is the natural place to show the model extending.

### 3.5 Authorizing an action
Every protected action declares a **required role**:
```
authorize(principal, repo, action):
    return effective_role(principal, repo) >= action.required_role
```

### 3.6 Branch protection
A protected branch adds rules evaluated at ref-update time: disallow force-push and deletion, require PR approval before merge, restrict who may push directly. Enforced in the Git receive path (section 4.3), not only in the UI, so it cannot be bypassed from the command line.

## 4. Git protocol security

### 4.1 SSH transport
The connection authenticates the public key to a user before any Git command runs, and the invoked command is constrained to the Git upload and receive verbs. No arbitrary shell.

### 4.2 HTTP transport
Fetch and push carry a token or session; anonymous fetch is allowed only for public repos. The repo path in the URL is resolved to a repository object and checked; it is never used to build a filesystem path directly (section 6.3).

### 4.3 Ref-update authorization (the critical control)
Push is where access control is most often bypassed, so every proposed ref update is checked before it is accepted:
```
for each (ref, old, new) in the push:
    require effective_role(principal, repo) >= write
    if ref is protected:
        enforce branch protection rules (no force-push, approvals, push restrictions)
    else:
        accept
```
Only after all updates pass are refs written. A violating push is rejected atomically.

## 5. Secrets and data handling
- Password hashes: argon2id, per-user salt.
- Token storage: hash only, never plaintext.
- SSH private keys are never held by the server; only public keys are stored.
- Outbound webhook secrets are stored to sign payloads (HMAC) so receivers can verify authenticity.
- No secret is ever written to logs.

## 6. Threat model

Read this as an attacker would.

### 6.1 Assets
Private repository contents; credentials (session ids, tokens, password hashes); integrity of history (no rewriting protected branches); availability of the service.

### 6.2 Trust boundaries
Anonymous internet to the web/API surface; authenticated user to resources not granted; Git client to the object store and filesystem; API to the metadata store and object storage.

### 6.3 Threats and mitigations

| Threat | Vector | Mitigation |
|---|---|---|
| Private repo disclosure (IDOR) | Enumerating repo/PR/issue ids on read endpoints | Authorize every read against `effective_role`; return identical "not found" for "does not exist" and "not permitted" so existence is not leaked |
| Access-control bypass on push | Pushing from CLI past UI-only checks | Authorize every ref update server-side (section 4.3) |
| Protected-branch rewrite | Force-push or delete of `main` | Branch protection enforced at ref-update time, not just in the UI |
| Token or session theft | Leaked token, stolen cookie | Scoped tokens, hashed storage, expiry and revocation; `HttpOnly`/`Secure`/`SameSite` cookies; CSRF tokens on mutations |
| Path traversal in object store | Malicious repo name or object path building a filesystem path | Resolve names to ids and use the id as the storage key; never concatenate user input into a path; allowlist name patterns |
| Delta / decompression bomb | A crafted pack with deeply nested deltas or a highly compressible object that explodes on read | Bound delta-chain depth (a fixed cap); cap decompressed object size; reject packs that exceed size or object-count limits before fully processing them |
| Resource-exhaustion DoS | Enormous pushed pack, pathological history, expensive search, unbounded team walk | Size and count limits on packs; timeouts and bounds on graph walks, merge, and search; a depth cap and visited set on the team-hierarchy walk (section 3.4); per-principal rate limits |
| Injection (SQL, command) | User content into queries or shell | Parameterized queries; no shell from the Git path; the SSH command is constrained to Git verbs |
| Privilege confusion via teams | Nested or cyclic team membership granting more than intended, or looping resolution | Resolution is an explicit maximum over enumerated sources; the team walk is bounded and tested with allow and deny cases and with a cyclic-structure case |
| Webhook SSRF | Webhook URL pointed at internal services | Validate and restrict webhook targets; deny internal address ranges |

### 6.4 Explicitly out of scope for v1
Encryption at rest, hardware-key attestation, tamper-proof audit logs, and anti-abuse/spam systems. Named so their absence is a decision, not an oversight.

## 7. Audit logging
Record security-relevant events with actor, action, resource, and outcome: logins, token creation and revocation, permission grants and changes, ref updates on protected branches, repository deletion. Append-only in intent, never containing secrets.

## 8. Build-time security checklist
- [ ] Every read endpoint calls `authorize` before returning data.
- [ ] Every ref update is authorized in the receive path.
- [ ] "Not found" and "not permitted" are indistinguishable to the client.
- [ ] Passwords use argon2id; tokens are stored hashed.
- [ ] Storage keys are ids, never raw user-supplied names.
- [ ] Delta-chain depth, decompressed object size, pack size, and object count are all bounded.
- [ ] Graph walks, merge, search, and the team-hierarchy walk have bounds and timeouts.
- [ ] Cookies are `HttpOnly`, `Secure`, `SameSite`; CSRF tokens on mutations.
- [ ] An automated suite asserts allow and deny for each role on each protected action, including a cyclic-team case.
