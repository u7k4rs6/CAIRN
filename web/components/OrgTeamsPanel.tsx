"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/AuthBar";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { apiBase } from "@/lib/apiBase";

const API_BASE = apiBase();

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
    return <p className="text-ink-muted text-sm">Sign in above to manage teams for this organization.</p>;
  }
  if (deniedOrMissing) {
    return (
      <div className="max-w-lg px-4 py-12 text-center">
        <h1 className="text-lg font-display font-bold text-ink mb-2">Off the map</h1>
        <p className="text-ink-2">This organization isn&rsquo;t here, or you don&rsquo;t administer it.</p>
      </div>
    );
  }
  if (!teams) {
    return <div className="text-sm text-ink-muted animate-pulse">Loading teams&hellip;</div>;
  }

  return (
    <div className="max-w-2xl px-4 py-4 flex flex-col gap-4">
      <h1 className="text-lg font-mono text-ink">
        {org} <span className="text-contour">&#9656;</span> teams
      </h1>
      {error && <div className="border border-survey-red text-survey-red text-sm rounded p-2">{error}</div>}

      <ul className="border border-hairline rounded divide-y divide-hairline">
        {teams.length === 0 && <li className="p-2 text-sm text-ink-muted">No teams yet.</li>}
        {teams.map((t) => (
          <li key={t.id} className="p-3 flex flex-col gap-2 text-sm">
            <div>
              <span className="font-medium text-ink">{t.name}</span>
              {t.parentTeam && <span className="text-ink-muted"> (nested under {t.parentTeam})</span>}
            </div>
            <div className="flex items-center gap-2">
              <Input
                placeholder="username to add"
                value={memberInputs[t.name] || ""}
                onChange={(e) => setMemberInputs((prev) => ({ ...prev, [t.name]: e.target.value }))}
                className="flex-1"
              />
              <Button variant="secondary" onClick={() => addMember(t.name)} className="text-xs py-1">
                Add member
              </Button>
            </div>
          </li>
        ))}
      </ul>

      <div className="border border-hairline rounded p-3 flex items-end gap-2">
        <label className="flex flex-col gap-1 flex-1">
          <span className="text-xs text-ink-muted">New team name</span>
          <Input value={name} onChange={(e) => setName(e.target.value)} className="font-mono" />
        </label>
        <label className="flex flex-col gap-1 flex-1">
          <span className="text-xs text-ink-muted">Parent team (optional)</span>
          <Input value={parentTeam} onChange={(e) => setParentTeam(e.target.value)} className="font-mono" />
        </label>
        <Button variant="primary" onClick={createTeam}>
          Create team
        </Button>
      </div>
    </div>
  );
}
