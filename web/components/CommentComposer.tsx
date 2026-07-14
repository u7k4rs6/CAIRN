"use client";

import { useState } from "react";
import { useAuth } from "@/components/AuthBar";
import { Textarea } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

export function CommentComposer({ owner, repo, issueId }: { owner: string; repo: string; issueId: number }) {
  const { isAuthenticated, authHeader } = useAuth();
  const [body, setBody] = useState("");
  const [submitted, setSubmitted] = useState(false);

  if (!isAuthenticated) {
    return null;
  }
  if (submitted) {
    return <p className="text-veg text-sm">Comment posted.</p>;
  }

  async function submit() {
    await fetch(`${API_BASE}/api/repos/${owner}/${repo}/issues/${issueId}/comments`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ body }),
    });
    setSubmitted(true);
  }

  return (
    <div className="border border-hairline rounded p-3 flex flex-col gap-2 bg-surface">
      <Textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="Leave a comment" rows={3} />
      <Button variant="primary" onClick={submit} className="self-start">
        Comment
      </Button>
    </div>
  );
}
