"use client";

import { useState } from "react";
import { useAuth } from "@/components/AuthBar";
import { Textarea, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { apiBase } from "@/lib/apiBase";

const API_BASE = apiBase();

export function ReviewComposer({ owner, repo, number }: { owner: string; repo: string; number: number }) {
  const { isAuthenticated, authHeader } = useAuth();
  const [body, setBody] = useState("");
  const [verdict, setVerdict] = useState<"APPROVE" | "REQUEST_CHANGES" | "COMMENT">("COMMENT");
  const [submitted, setSubmitted] = useState(false);

  if (!isAuthenticated) {
    return null;
  }
  if (submitted) {
    return <p className="text-veg text-sm">Review submitted.</p>;
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
    <div className="border border-hairline rounded p-3 flex flex-col gap-2 bg-surface">
      <Textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="Leave a review comment"
        rows={3}
      />
      <div className="flex items-center gap-2">
        <Select value={verdict} onChange={(e) => setVerdict(e.target.value as typeof verdict)} aria-label="Review verdict">
          <option value="COMMENT">Comment</option>
          <option value="APPROVE">Approve</option>
          <option value="REQUEST_CHANGES">Request changes</option>
        </Select>
        <Button variant="primary" onClick={submit}>
          Submit review
        </Button>
      </div>
    </div>
  );
}
