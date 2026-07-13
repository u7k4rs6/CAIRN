"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/AuthBar";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

type Team = { id: number; name: string; parentTeam: string | null };

/**
 * Frontend spec route {@code /orgs/{org}/teams} (section 3): the "org admin manages
 * teams" screen. Client-rendered for the same reason {@link AccessSettingsPanel} is:
 * the access token lives in localStorage, invisible to a Server Component.
 */
export function OrgTeamsPanel({ org }: { org: string }) {
  const { isAuthenticated, authHeader } = useAuth();
  const [teams, setTeams] = useState<Team[] | null>(null);
  const [deniedOrMissing, setDeniedOrMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [parentTeam, setParentTeam] = useState("");
  const [memberInputs, setMemberInputs] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!isAuthenticated) return;
    const res = await fetch(`${API_BASE}/api/orgs/${org}/teams`, { headers: { ...authHeader }, cache: "no-store" });
    if (res.status === 404 || res.status === 403) {
      setDeniedOrMissing(true);
      return;
    }
    setDeniedOrMissing(false);
    setTeams(await res.json());
  }, [org, authHeader, isAuthenticated]);

  useEffect(() => {
    load();
  }, [load]);

  async function createTeam() {
    setError(null);
    const res = await fetch(`${API_BASE}/api/orgs/${org}/teams`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ name, parentTeam: parentTeam || null }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `request failed: ${res.status}` }));
      setError(body.error || `request failed: ${res.status}`);
      return;
    }
    setName("");
    setParentTeam("");
    await load();
  }

  async function addMember(teamName: string) {
    const username = memberInputs[teamName];
    if (!username) return;
    setError(null);
    const res = await fetch(`${API_BASE}/api/orgs/${org}/teams/${teamName}/members`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ username }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `request failed: ${res.status}` }));
      setError(body.error || `request failed: ${res.status}`);
      return;
    }
    setMemberInputs((prev) => ({ ...prev, [teamName]: "" }));
  }

  if (!isAuthenticated) {
    return <p className="text-fg-muted text-sm">Sign in above to manage teams for this organization.</p>;
  }
  if (deniedOrMissing) {
    return (
      <div className="max-w-lg px-4 py-12 text-center">
        <h1 className="text-lg font-semibold mb-2">404</h1>
        <p className="text-fg-muted">This organization does not exist, or you do not administer it.</p>
      </div>
    );
  }
  if (!teams) {
    return <div className="text-sm text-fg-muted animate-pulse">Loading teams...</div>;
  }

  return (
    <div className="max-w-2xl px-4 py-4 flex flex-col gap-4">
      <h1 className="text-lg font-semibold">{org} / teams</h1>
      {error && <div className="border border-danger text-danger text-sm rounded p-2">{error}</div>}

      <ul className="border border-border rounded divide-y divide-border">
        {teams.length === 0 && <li className="p-2 text-sm text-fg-muted">No teams yet.</li>}
        {teams.map((t) => (
          <li key={t.id} className="p-3 flex flex-col gap-2 text-sm">
            <div>
              <span className="font-medium">{t.name}</span>
              {t.parentTeam && <span className="text-fg-muted"> (nested under {t.parentTeam})</span>}
            </div>
            <div className="flex items-center gap-2">
              <input
                placeholder="username to add"
                value={memberInputs[t.name] || ""}
                onChange={(e) => setMemberInputs((prev) => ({ ...prev, [t.name]: e.target.value }))}
                className="border border-border rounded px-2 py-1 bg-bg text-sm flex-1"
              />
              <button onClick={() => addMember(t.name)} className="bg-accent text-accent-fg rounded px-2 py-1 text-xs font-medium">
                Add member
              </button>
            </div>
          </li>
        ))}
      </ul>

      <div className="border border-border rounded p-3 flex items-end gap-2">
        <label className="flex flex-col gap-1 flex-1">
          <span className="text-xs text-fg-muted">New team name</span>
          <input value={name} onChange={(e) => setName(e.target.value)} className="border border-border rounded px-2 py-1.5 bg-bg text-sm" />
        </label>
        <label className="flex flex-col gap-1 flex-1">
          <span className="text-xs text-fg-muted">Parent team (optional)</span>
          <input
            value={parentTeam}
            onChange={(e) => setParentTeam(e.target.value)}
            className="border border-border rounded px-2 py-1.5 bg-bg text-sm"
          />
        </label>
        <button onClick={createTeam} className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium">
          Create team
        </button>
      </div>
    </div>
  );
}
