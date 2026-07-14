"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/AuthBar";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

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
    return <p className="text-ink-muted text-sm">Sign in above to manage this repository&rsquo;s access.</p>;
  }
  if (deniedOrMissing) {
    return (
      <div className="max-w-lg px-4 py-12 text-center">
        <h1 className="text-lg font-display font-bold text-ink mb-2">Off the map</h1>
        <p className="text-ink-2">This repository isn&rsquo;t here, or you don&rsquo;t have admin access to it.</p>
      </div>
    );
  }
  if (!access) {
    return <div className="text-sm text-ink-muted animate-pulse">Loading access settings&hellip;</div>;
  }

  return (
    <div className="max-w-2xl px-4 py-4 flex flex-col gap-8">
      {error && <div className="border border-survey-red text-survey-red text-sm rounded p-2">{error}</div>}

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
      <h2 className="font-mono text-xs uppercase tracking-wide text-ink-muted">{title}</h2>
      {children}
    </section>
  );
}

function RoleSelect({ value, onChange }: { value: Role; onChange: (r: Role) => void }) {
  return (
    <Select value={value} onChange={(e) => onChange(e.target.value as Role)} aria-label="Role">
      {ROLES.map((r) => (
        <option key={r} value={r}>
          {r.charAt(0) + r.slice(1).toLowerCase()}
        </option>
      ))}
    </Select>
  );
}

function VisibilitySection({ visibility, onSave }: { visibility: Visibility; onSave: (v: Visibility) => Promise<boolean> }) {
  const [value, setValue] = useState(visibility);
  return (
    <Section title="Visibility">
      <div className="flex items-center gap-2">
        <Select value={value} onChange={(e) => setValue(e.target.value as Visibility)}>
          <option value="PUBLIC">Public</option>
          <option value="INTERNAL">Internal</option>
          <option value="PRIVATE">Private</option>
        </Select>
        <Button variant="primary" onClick={() => onSave(value)}>
          Save
        </Button>
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
      <ul className="border border-hairline rounded divide-y divide-hairline">
        {collaborators.length === 0 && <li className="p-2 text-sm text-ink-muted">No direct collaborators.</li>}
        {collaborators.map((c) => (
          <li key={c.username} className="p-2 flex items-center justify-between text-sm">
            <span className="text-ink">
              {c.username} <span className="text-ink-muted">({c.role.toLowerCase()})</span>
            </span>
            <button onClick={() => onRemove(c.username)} className="text-survey-red text-xs hover:underline">
              Remove
            </button>
          </li>
        ))}
      </ul>
      <div className="flex items-end gap-2">
        <Input placeholder="username" value={username} onChange={(e) => setUsername(e.target.value)} className="flex-1 font-mono" />
        <RoleSelect value={role} onChange={setRole} />
        <Button
          variant="primary"
          onClick={async () => {
            if (await onAdd(username, role)) setUsername("");
          }}
        >
          Add
        </Button>
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
      <ul className="border border-hairline rounded divide-y divide-hairline">
        {teamGrants.length === 0 && <li className="p-2 text-sm text-ink-muted">No team grants.</li>}
        {teamGrants.map((g) => (
          <li key={`${g.org}/${g.team}`} className="p-2 flex items-center justify-between text-sm">
            <span className="text-ink font-mono">
              {g.org}/{g.team} <span className="text-ink-muted font-sans">({g.role.toLowerCase()})</span>
            </span>
            <button onClick={() => onRemove(g.org, g.team)} className="text-survey-red text-xs hover:underline">
              Remove
            </button>
          </li>
        ))}
      </ul>
      <div className="flex items-end gap-2">
        <Input placeholder="org" value={org} onChange={(e) => setOrg(e.target.value)} className="w-24 font-mono" />
        <Input placeholder="team" value={team} onChange={(e) => setTeam(e.target.value)} className="w-24 font-mono" />
        <RoleSelect value={role} onChange={setRole} />
        <Button
          variant="primary"
          onClick={async () => {
            if (await onAdd(org, team, role)) {
              setOrg("");
              setTeam("");
            }
          }}
        >
          Add
        </Button>
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
      <ul className="border border-hairline rounded divide-y divide-hairline">
        {rules.length === 0 && <li className="p-2 text-sm text-ink-muted">No protected branches.</li>}
        {rules.map((r) => {
          const name = r.ref.replace(/^refs\/heads\//, "");
          return (
            <li key={r.ref} className="p-2 flex items-center justify-between text-sm">
              <span>
                <span className="font-mono text-ink">{name}</span>{" "}
                <span className="text-ink-muted">
                  {r.preventForcePush && "no force-push"}
                  {r.preventDeletion && ", no deletion"}
                  {r.requireApprovalBeforeMerge && ", approval required"}
                </span>
              </span>
              <button onClick={() => onRemove(name)} className="text-survey-red text-xs hover:underline">
                Remove
              </button>
            </li>
          );
        })}
      </ul>
      <div className="border border-hairline rounded p-3 flex flex-col gap-2 text-sm bg-surface">
        <Input placeholder="branch name, e.g. main" value={branch} onChange={(e) => setBranch(e.target.value)} className="w-48 font-mono" />
        <label className="flex items-center gap-2 text-ink-2">
          <input type="checkbox" checked={preventForcePush} onChange={(e) => setPreventForcePush(e.target.checked)} />
          Prevent force-push
        </label>
        <label className="flex items-center gap-2 text-ink-2">
          <input type="checkbox" checked={preventDeletion} onChange={(e) => setPreventDeletion(e.target.checked)} />
          Prevent deletion
        </label>
        <label className="flex items-center gap-2 text-ink-2">
          <input
            type="checkbox"
            checked={requireApprovalBeforeMerge}
            onChange={(e) => setRequireApprovalBeforeMerge(e.target.checked)}
          />
          Require approval before merge
        </label>
        <div className="flex items-center gap-2 text-ink-2">
          <span>Minimum role to push directly:</span>
          <RoleSelect value={minimumPushRole} onChange={setMinimumPushRole} />
        </div>
        <Button
          variant="primary"
          onClick={() =>
            onSave({ ref: branch, preventForcePush, preventDeletion, requireApprovalBeforeMerge, minimumPushRole })
          }
          className="self-start"
        >
          Save rule
        </Button>
      </div>
    </Section>
  );
}
