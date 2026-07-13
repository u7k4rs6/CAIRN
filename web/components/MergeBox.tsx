"use client";

import { useState } from "react";
import { useAuth } from "@/components/AuthBar";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * Frontend spec, section 5.6: reflects mergeability and permission, showing the
 * available strategies only when the user can act, otherwise why it's blocked.
 * Here "why blocked" comes back as the API's own error message (insufficient role,
 * requires approval, etc.) rather than the UI trying to pre-compute every reason.
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
  const [merged, setMerged] = useState(state === "MERGED");

  if (merged) {
    return <div className="state-badge bg-accent/15 text-accent">Merged</div>;
  }
  if (state === "CLOSED") {
    return <p className="text-fg-muted text-sm">This pull request is closed.</p>;
  }
  if (!isAuthenticated) {
    return <p className="text-fg-muted text-sm">Sign in above to merge this pull request.</p>;
  }

  async function merge() {
    setStatus("Merging...");
    const res = await fetch(`${API_BASE}/api/repos/${owner}/${repo}/pulls/${number}/merge`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ strategy }),
    });
    if (res.ok) {
      setMerged(true);
      setStatus(null);
    } else {
      const text = await res.text();
      setStatus(`Blocked: ${text}`);
    }
  }

  return (
    <div className="border border-border rounded p-3 flex items-center gap-2">
      <select
        value={strategy}
        onChange={(e) => setStrategy(e.target.value as "MERGE_COMMIT" | "SQUASH" | "REBASE")}
        className="border border-border rounded px-2 py-1.5 bg-bg text-sm"
      >
        <option value="MERGE_COMMIT">Create a merge commit</option>
        <option value="SQUASH">Squash and merge</option>
        <option value="REBASE">Rebase and merge</option>
      </select>
      <button onClick={merge} className="bg-success text-white rounded px-3 py-1.5 text-sm font-medium">
        Merge pull request
      </button>
      {status && <span className="text-danger text-sm">{status}</span>}
    </div>
  );
}
