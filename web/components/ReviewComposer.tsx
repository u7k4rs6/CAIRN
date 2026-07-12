"use client";

import { useState } from "react";
import { useAuth } from "@/components/AuthBar";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

export function ReviewComposer({ owner, repo, number }: { owner: string; repo: string; number: number }) {
  const { isAuthenticated, authHeader } = useAuth();
  const [body, setBody] = useState("");
  const [verdict, setVerdict] = useState<"APPROVE" | "REQUEST_CHANGES" | "COMMENT">("COMMENT");
  const [submitted, setSubmitted] = useState(false);

  if (!isAuthenticated) {
    return null;
  }
  if (submitted) {
    return <p className="text-fg-muted text-sm">Review submitted.</p>;
  }

  async function submit() {
    await fetch(`${API_BASE}/api/repos/${owner}/${repo}/pulls/${number}/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ verdict, body }),
    });
    setSubmitted(true);
  }

  return (
    <div className="border border-border rounded p-3 flex flex-col gap-2">
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="Leave a review comment"
        rows={3}
        className="border border-border rounded px-2 py-1.5 bg-bg text-sm"
      />
      <div className="flex items-center gap-2">
        <select
          value={verdict}
          onChange={(e) => setVerdict(e.target.value as typeof verdict)}
          className="border border-border rounded px-2 py-1.5 bg-bg text-sm"
        >
          <option value="COMMENT">Comment</option>
          <option value="APPROVE">Approve</option>
          <option value="REQUEST_CHANGES">Request changes</option>
        </select>
        <button onClick={submit} className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium">
          Submit review
        </button>
      </div>
    </div>
  );
}
