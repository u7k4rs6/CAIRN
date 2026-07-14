"use client";

import { useState } from "react";
import { useAuth } from "@/components/AuthBar";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Input";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

type Conflict = { path: string; base: string[]; ours: string[]; theirs: string[] };

/**
 * Frontend spec, section 5.6: reflects mergeability and permission, showing the
 * available strategies only when the user can act, otherwise why it's blocked.
 * "Why blocked" for a plain permission/state failure is the API's own error text;
 * a real merge conflict is a distinct, richer case - the merge endpoint returns 409
 * with the actual list of conflicting paths (`PullRequestService.MergeResult`'s
 * `Conflict` records), which this now parses and renders instead of just the raw
 * response text a conflict used to show.
 */
export function MergeBox({
  owner,
  repo,
  number,
  state,
}: {
  owner: string;
  repo: string;
  number: number;
  state: string;
}) {
  const { isAuthenticated, authHeader } = useAuth();
  const [strategy, setStrategy] = useState<"MERGE_COMMIT" | "SQUASH" | "REBASE">("MERGE_COMMIT");
  const [status, setStatus] = useState<string | null>(null);
  const [conflicts, setConflicts] = useState<Conflict[] | null>(null);
  const [merging, setMerging] = useState(false);
  const [merged, setMerged] = useState(state === "MERGED");

  if (merged) {
    return <span className="state-chip bg-route/15 text-route-ink">merged</span>;
  }
  if (state === "CLOSED") {
    return <p className="text-ink-muted text-sm">This pull request is closed.</p>;
  }
  if (!isAuthenticated) {
    return <p className="text-ink-muted text-sm">Sign in above to merge this pull request.</p>;
  }

  async function merge() {
    setMerging(true);
    setStatus(null);
    setConflicts(null);
    const res = await fetch(`${API_BASE}/api/repos/${owner}/${repo}/pulls/${number}/merge`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ strategy }),
    });
    if (res.status === 409) {
      setConflicts(await res.json());
      setMerging(false);
      return;
    }
    if (res.ok) {
      setMerged(true);
      setMerging(false);
      return;
    }
    const text = await res.text();
    setStatus(text || `Merge blocked (${res.status}).`);
    setMerging(false);
  }

  return (
    <div className="border border-hairline rounded p-3 flex flex-col gap-3 bg-surface">
      <div className="flex items-center gap-2 flex-wrap">
        <Select
          value={strategy}
          onChange={(e) => setStrategy(e.target.value as "MERGE_COMMIT" | "SQUASH" | "REBASE")}
          aria-label="Merge strategy"
        >
          <option value="MERGE_COMMIT">Create a merge commit</option>
          <option value="SQUASH">Squash and merge</option>
          <option value="REBASE">Rebase and merge</option>
        </Select>
        <Button variant="primary" onClick={merge} disabled={merging}>
          {merging ? "Merging…" : "Merge pull request"}
        </Button>
      </div>

      {status && <p className="text-survey-red text-sm">{status}</p>}

      {conflicts && conflicts.length > 0 && (
        <div className="border border-caution rounded bg-conflict-bg p-3 flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <span className="state-chip bg-caution/25 text-caution">merge conflict</span>
            <span className="text-sm text-ink-2">
              {conflicts.length} file{conflicts.length === 1 ? "" : "s"} can&rsquo;t be merged automatically.
            </span>
          </div>
          <ul className="font-mono text-xs text-ink-2 flex flex-col gap-1">
            {conflicts.map((c) => (
              <li key={c.path}>{c.path}</li>
            ))}
          </ul>
          <p className="text-xs text-ink-muted">
            Resolve these paths locally and push an update to the source branch, then merge again.
          </p>
        </div>
      )}
    </div>
  );
}
