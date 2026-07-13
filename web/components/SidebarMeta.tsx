"use client";

import { useCallback, useEffect, useState } from "react";
import { useAuth } from "@/components/AuthBar";
import { LabelChip } from "@/components/LabelChip";
import type { Issue, Label, Milestone } from "@/lib/api";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * Frontend spec, section 5.7: the issue detail sidebar (labels, assignees,
 * milestone). Read-only for anyone who can read the issue (labels/milestone are
 * fetched anonymously, same as the rest of this app's read paths); the add/remove
 * controls only render once signed in, and the API itself still enforces triage
 * on every mutation regardless of what this component shows.
 */
export function SidebarMeta({
  owner,
  repo,
  issue,
}: {
  owner: string;
  repo: string;
  issue: Issue;
}) {
  const { isAuthenticated, authHeader } = useAuth();
  const [labels, setLabels] = useState(issue.labels);
  const [assignees, setAssignees] = useState(issue.assignees);
  const [milestone, setMilestoneState] = useState(issue.milestone);
  const [repoLabels, setRepoLabels] = useState<Label[]>([]);
  const [repoMilestones, setRepoMilestones] = useState<Milestone[]>([]);
  const [assigneeInput, setAssigneeInput] = useState("");
  const [error, setError] = useState<string | null>(null);

  const base = `${API_BASE}/api/repos/${owner}/${repo}`;

  const loadOptions = useCallback(async () => {
    const [labelsRes, milestonesRes] = await Promise.all([
      fetch(`${base}/labels`, { cache: "no-store" }),
      fetch(`${base}/milestones`, { cache: "no-store" }),
    ]);
    if (labelsRes.ok) setRepoLabels(await labelsRes.json());
    if (milestonesRes.ok) setRepoMilestones(await milestonesRes.json());
  }, [base]);

  useEffect(() => {
    loadOptions();
  }, [loadOptions]);

  async function withErrorHandling(action: () => Promise<Response>): Promise<boolean> {
    setError(null);
    const res = await action();
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `request failed: ${res.status}` }));
      setError(body.error || `request failed: ${res.status}`);
      return false;
    }
    return true;
  }

  async function addLabel(labelId: number) {
    const ok = await withErrorHandling(() =>
      fetch(`${base}/issues/${issue.id}/labels`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeader },
        body: JSON.stringify({ labelId }),
      })
    );
    if (ok) {
      const added = repoLabels.find((l) => l.id === labelId);
      if (added) setLabels((prev) => [...prev, added]);
    }
  }

  async function removeLabel(labelId: number) {
    const ok = await withErrorHandling(() =>
      fetch(`${base}/issues/${issue.id}/labels/${labelId}`, { method: "DELETE", headers: { ...authHeader } })
    );
    if (ok) setLabels((prev) => prev.filter((l) => l.id !== labelId));
  }

  async function addAssignee() {
    if (!assigneeInput) return;
    const ok = await withErrorHandling(() =>
      fetch(`${base}/issues/${issue.id}/assignees`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authHeader },
        body: JSON.stringify({ username: assigneeInput }),
      })
    );
    if (ok) {
      setAssignees((prev) => [...prev, { username: assigneeInput }]);
      setAssigneeInput("");
    }
  }

  async function removeAssignee(username: string) {
    const ok = await withErrorHandling(() =>
      fetch(`${base}/issues/${issue.id}/assignees/${username}`, { method: "DELETE", headers: { ...authHeader } })
    );
    if (ok) setAssignees((prev) => prev.filter((a) => a.username !== username));
  }

  async function setMilestone(milestoneId: number | null) {
    const ok = await withErrorHandling(() =>
      fetch(`${base}/issues/${issue.id}/milestone`, {
        method: "PUT",
        headers: { "Content-Type": "application/json", ...authHeader },
        body: JSON.stringify({ milestoneId }),
      })
    );
    if (ok) setMilestoneState(repoMilestones.find((m) => m.id === milestoneId) ?? null);
  }

  return (
    <div className="border border-border rounded p-3 flex flex-col gap-4 text-sm w-56 shrink-0">
      {error && <div className="text-danger text-xs">{error}</div>}

      <div>
        <div className="text-xs font-semibold uppercase text-fg-muted mb-2">Labels</div>
        <div className="flex flex-wrap gap-1 mb-2">
          {labels.length === 0 && <span className="text-fg-muted text-xs">None</span>}
          {labels.map((l) => (
            <span key={l.id} className="inline-flex items-center gap-1">
              <LabelChip label={l} />
              {isAuthenticated && (
                <button onClick={() => removeLabel(l.id)} className="text-fg-muted hover:text-danger text-xs">
                  x
                </button>
              )}
            </span>
          ))}
        </div>
        {isAuthenticated && (
          <select
            value=""
            onChange={(e) => e.target.value && addLabel(Number(e.target.value))}
            className="border border-border rounded px-1 py-0.5 bg-bg text-xs w-full"
          >
            <option value="">Add label...</option>
            {repoLabels.filter((l) => !labels.some((existing) => existing.id === l.id)).map((l) => (
              <option key={l.id} value={l.id}>
                {l.name}
              </option>
            ))}
          </select>
        )}
      </div>

      <div>
        <div className="text-xs font-semibold uppercase text-fg-muted mb-2">Assignees</div>
        <div className="flex flex-col gap-1 mb-2">
          {assignees.length === 0 && <span className="text-fg-muted text-xs">None</span>}
          {assignees.map((a) => (
            <div key={a.username} className="flex items-center justify-between">
              <span>{a.username}</span>
              {isAuthenticated && (
                <button onClick={() => removeAssignee(a.username)} className="text-fg-muted hover:text-danger text-xs">
                  x
                </button>
              )}
            </div>
          ))}
        </div>
        {isAuthenticated && (
          <div className="flex gap-1">
            <input
              value={assigneeInput}
              onChange={(e) => setAssigneeInput(e.target.value)}
              placeholder="username"
              className="border border-border rounded px-1 py-0.5 bg-bg text-xs flex-1 min-w-0"
            />
            <button onClick={addAssignee} className="text-xs border border-border rounded px-1.5">
              Add
            </button>
          </div>
        )}
      </div>

      <div>
        <div className="text-xs font-semibold uppercase text-fg-muted mb-2">Milestone</div>
        {isAuthenticated ? (
          <select
            value={milestone?.id ?? ""}
            onChange={(e) => setMilestone(e.target.value ? Number(e.target.value) : null)}
            className="border border-border rounded px-1 py-0.5 bg-bg text-xs w-full"
          >
            <option value="">None</option>
            {repoMilestones.map((m) => (
              <option key={m.id} value={m.id}>
                {m.title}
              </option>
            ))}
          </select>
        ) : (
          <span className="text-fg-muted text-xs">{milestone?.title ?? "None"}</span>
        )}
      </div>
    </div>
  );
}
