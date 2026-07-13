"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/AuthBar";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

type Role = "READ" | "TRIAGE" | "WRITE" | "MAINTAIN" | "ADMIN";
type Visibility = "PUBLIC" | "INTERNAL" | "PRIVATE";

type Collaborator = { username: string; role: Role };
type TeamGrant = { org: string; team: string; role: Role };
type AccessView = { visibility: Visibility; collaborators: Collaborator[]; teamGrants: TeamGrant[] };
type BranchProtection = {
  ref: string;
  preventForcePush: boolean;
  preventDeletion: boolean;
  requireApprovalBeforeMerge: boolean;
  minimumPushRole: Role;
};

const ROLES: Role[] = ["READ", "TRIAGE", "WRITE", "MAINTAIN", "ADMIN"];

/**
 * Frontend spec, section 5.9: the visible face of the permission model. Fetches
 * and mutates entirely client-side (rather than through a Server Component, like
 * every read-only page in this app does) because the access token lives in
 * localStorage, which a Server Component cannot see; a real session cookie would
 * remove this asymmetry (see DECISIONS.md, P2 gap-closure item), but until then
 * this is the one page that must talk to the API from the browser to work at all.
 */
export function AccessSettingsPanel({ owner, repo }: { owner: string; repo: string }) {
  const { isAuthenticated, authHeader } = useAuth();
  const [access, setAccess] = useState<AccessView | null>(null);
  const [rules, setRules] = useState<BranchProtection[]>([]);
  const [deniedOrMissing, setDeniedOrMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const base = `${API_BASE}/api/repos/${owner}/${repo}`;

  const load = useCallback(async () => {
    if (!isAuthenticated) {
      return;
    }
    const [accessRes, rulesRes] = await Promise.all([
      fetch(`${base}/access`, { headers: { ...authHeader }, cache: "no-store" }),
      fetch(`${base}/branch-protection`, { headers: { ...authHeader }, cache: "no-store" }),
    ]);
    if (accessRes.status === 404) {
      setDeniedOrMissing(true);
      return;
    }
    setDeniedOrMissing(false);
    setAccess(await accessRes.json());
    setRules(rulesRes.ok ? await rulesRes.json() : []);
  }, [base, authHeader, isAuthenticated]);

  useEffect(() => {
    load();
  }, [load]);

  async function withErrorHandling(action: () => Promise<Response>) {
    setError(null);
    const res = await action();
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `request failed: ${res.status}` }));
      setError(body.error || `request failed: ${res.status}`);
      return false;
    }
    await load();
    return true;
  }

  if (!isAuthenticated) {
    return <p className="text-fg-muted text-sm">Sign in above to manage this repository's access.</p>;
  }
  if (deniedOrMissing) {
    return (
      <div className="max-w-lg px-4 py-12 text-center">
        <h1 className="text-lg font-semibold mb-2">404</h1>
        <p className="text-fg-muted">This repository does not exist, or you do not have admin access to it.</p>
      </div>
    );
  }
  if (!access) {
    return <div className="text-sm text-fg-muted animate-pulse">Loading access settings...</div>;
  }

  return (
    <div className="max-w-2xl px-4 py-4 flex flex-col gap-8">
      {error && <div className="border border-danger text-danger text-sm rounded p-2">{error}</div>}

      <VisibilitySection
        visibility={access.visibility}
        onSave={(v) =>
          withErrorHandling(() =>
            fetch(`${base}/visibility`, {
              method: "PUT",
              headers: { "Content-Type": "application/json", ...authHeader },
              body: JSON.stringify({ visibility: v }),
            })
          )
        }
      />

      <CollaboratorSection
        collaborators={access.collaborators}
        onAdd={(username, role) =>
          withErrorHandling(() =>
            fetch(`${base}/access/collaborators`, {
              method: "POST",
              headers: { "Content-Type": "application/json", ...authHeader },
              body: JSON.stringify({ username, role }),
            })
          )
        }
        onRemove={(username) =>
          withErrorHandling(() =>
            fetch(`${base}/access/collaborators/${username}`, { method: "DELETE", headers: { ...authHeader } })
          )
        }
      />

      <TeamGrantSection
        teamGrants={access.teamGrants}
        onAdd={(org, team, role) =>
          withErrorHandling(() =>
            fetch(`${base}/access/teams`, {
              method: "POST",
              headers: { "Content-Type": "application/json", ...authHeader },
              body: JSON.stringify({ org, team, role }),
            })
          )
        }
        onRemove={(org, team) =>
          withErrorHandling(() =>
            fetch(`${base}/access/teams/${org}/${team}`, { method: "DELETE", headers: { ...authHeader } })
          )
        }
      />

      <BranchProtectionSection
        rules={rules}
        onSave={(rule) =>
          withErrorHandling(() =>
            fetch(`${base}/branch-protection/${rule.ref}`, {
              method: "PUT",
              headers: { "Content-Type": "application/json", ...authHeader },
              body: JSON.stringify(rule),
            })
          )
        }
        onRemove={(ref) =>
          withErrorHandling(() => fetch(`${base}/branch-protection/${ref}`, { method: "DELETE", headers: { ...authHeader } }))
        }
      />
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-fg-muted">{title}</h2>
      {children}
    </section>
  );
}

function RoleSelect({ value, onChange }: { value: Role; onChange: (r: Role) => void }) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as Role)}
      className="border border-border rounded px-2 py-1.5 bg-bg text-sm"
    >
      {ROLES.map((r) => (
        <option key={r} value={r}>
          {r.charAt(0) + r.slice(1).toLowerCase()}
        </option>
      ))}
    </select>
  );
}

function VisibilitySection({ visibility, onSave }: { visibility: Visibility; onSave: (v: Visibility) => Promise<boolean> }) {
  const [value, setValue] = useState(visibility);
  return (
    <Section title="Visibility">
      <div className="flex items-center gap-2">
        <select
          value={value}
          onChange={(e) => setValue(e.target.value as Visibility)}
          className="border border-border rounded px-2 py-1.5 bg-bg text-sm"
        >
          <option value="PUBLIC">Public</option>
          <option value="INTERNAL">Internal</option>
          <option value="PRIVATE">Private</option>
        </select>
        <button onClick={() => onSave(value)} className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium">
          Save
        </button>
      </div>
    </Section>
  );
}

function CollaboratorSection({
  collaborators,
  onAdd,
  onRemove,
}: {
  collaborators: Collaborator[];
  onAdd: (username: string, role: Role) => Promise<boolean>;
  onRemove: (username: string) => Promise<boolean>;
}) {
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<Role>("READ");

  return (
    <Section title="Collaborators">
      <ul className="border border-border rounded divide-y divide-border">
        {collaborators.length === 0 && <li className="p-2 text-sm text-fg-muted">No direct collaborators.</li>}
        {collaborators.map((c) => (
          <li key={c.username} className="p-2 flex items-center justify-between text-sm">
            <span>
              {c.username} <span className="text-fg-muted">({c.role.toLowerCase()})</span>
            </span>
            <button onClick={() => onRemove(c.username)} className="text-danger text-xs hover:underline">
              Remove
            </button>
          </li>
        ))}
      </ul>
      <div className="flex items-end gap-2">
        <input
          placeholder="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="border border-border rounded px-2 py-1.5 bg-bg text-sm flex-1"
        />
        <RoleSelect value={role} onChange={setRole} />
        <button
          onClick={async () => {
            if (await onAdd(username, role)) setUsername("");
          }}
          className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium"
        >
          Add
        </button>
      </div>
    </Section>
  );
}

function TeamGrantSection({
  teamGrants,
  onAdd,
  onRemove,
}: {
  teamGrants: TeamGrant[];
  onAdd: (org: string, team: string, role: Role) => Promise<boolean>;
  onRemove: (org: string, team: string) => Promise<boolean>;
}) {
  const [org, setOrg] = useState("");
  const [team, setTeam] = useState("");
  const [role, setRole] = useState<Role>("WRITE");

  return (
    <Section title="Team grants">
      <ul className="border border-border rounded divide-y divide-border">
        {teamGrants.length === 0 && <li className="p-2 text-sm text-fg-muted">No team grants.</li>}
        {teamGrants.map((g) => (
          <li key={`${g.org}/${g.team}`} className="p-2 flex items-center justify-between text-sm">
            <span>
              {g.org}/{g.team} <span className="text-fg-muted">({g.role.toLowerCase()})</span>
            </span>
            <button onClick={() => onRemove(g.org, g.team)} className="text-danger text-xs hover:underline">
              Remove
            </button>
          </li>
        ))}
      </ul>
      <div className="flex items-end gap-2">
        <input
          placeholder="org"
          value={org}
          onChange={(e) => setOrg(e.target.value)}
          className="border border-border rounded px-2 py-1.5 bg-bg text-sm w-24"
        />
        <input
          placeholder="team"
          value={team}
          onChange={(e) => setTeam(e.target.value)}
          className="border border-border rounded px-2 py-1.5 bg-bg text-sm w-24"
        />
        <RoleSelect value={role} onChange={setRole} />
        <button
          onClick={async () => {
            if (await onAdd(org, team, role)) {
              setOrg("");
              setTeam("");
            }
          }}
          className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium"
        >
          Add
        </button>
      </div>
    </Section>
  );
}

function BranchProtectionSection({
  rules,
  onSave,
  onRemove,
}: {
  rules: BranchProtection[];
  onSave: (rule: BranchProtection) => Promise<boolean>;
  onRemove: (ref: string) => Promise<boolean>;
}) {
  const [branch, setBranch] = useState("main");
  const [preventForcePush, setPreventForcePush] = useState(true);
  const [preventDeletion, setPreventDeletion] = useState(true);
  const [requireApprovalBeforeMerge, setRequireApprovalBeforeMerge] = useState(false);
  const [minimumPushRole, setMinimumPushRole] = useState<Role>("WRITE");

  return (
    <Section title="Branch protection">
      <ul className="border border-border rounded divide-y divide-border">
        {rules.length === 0 && <li className="p-2 text-sm text-fg-muted">No protected branches.</li>}
        {rules.map((r) => {
          const name = r.ref.replace(/^refs\/heads\//, "");
          return (
            <li key={r.ref} className="p-2 flex items-center justify-between text-sm">
              <span>
                <span className="font-mono-data">{name}</span>{" "}
                <span className="text-fg-muted">
                  {r.preventForcePush && "no force-push"}
                  {r.preventDeletion && ", no deletion"}
                  {r.requireApprovalBeforeMerge && ", approval required"}
                </span>
              </span>
              <button onClick={() => onRemove(name)} className="text-danger text-xs hover:underline">
                Remove
              </button>
            </li>
          );
        })}
      </ul>
      <div className="border border-border rounded p-3 flex flex-col gap-2 text-sm">
        <input
          placeholder="branch name, e.g. main"
          value={branch}
          onChange={(e) => setBranch(e.target.value)}
          className="border border-border rounded px-2 py-1.5 bg-bg text-sm w-48"
        />
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={preventForcePush} onChange={(e) => setPreventForcePush(e.target.checked)} />
          Prevent force-push
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={preventDeletion} onChange={(e) => setPreventDeletion(e.target.checked)} />
          Prevent deletion
        </label>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={requireApprovalBeforeMerge}
            onChange={(e) => setRequireApprovalBeforeMerge(e.target.checked)}
          />
          Require approval before merge
        </label>
        <div className="flex items-center gap-2">
          <span>Minimum role to push directly:</span>
          <RoleSelect value={minimumPushRole} onChange={setMinimumPushRole} />
        </div>
        <button
          onClick={() =>
            onSave({ ref: branch, preventForcePush, preventDeletion, requireApprovalBeforeMerge, minimumPushRole })
          }
          className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium self-start"
        >
          Save rule
        </button>
      </div>
    </Section>
  );
}
