"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * Frontend spec route {@code /login} (section 3): real server-side sessions
 * (security doc, section 2.2), the P2 gap-closure replacement for localStorage-PAT
 * as the primary sign-in path. {@code credentials: "include"} is what makes the
 * browser actually store the {@code Set-Cookie} this call returns; every
 * subsequent same-origin-in-effect request (this app's Server Components forward
 * the cookie server-to-server, see lib/api.ts) then sees the session.
 */
export function LoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const router = useRouter();

  async function submit() {
    setSubmitting(true);
    setError(null);
    const res = await fetch(`${API_BASE}/api/login`, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `login failed: ${res.status}` }));
      setError(body.error || `login failed: ${res.status}`);
      setSubmitting(false);
      return;
    }
    router.push("/");
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-3 max-w-sm">
      {error && <div className="border border-survey-red text-survey-red text-sm rounded p-2">{error}</div>}
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-muted">Username</span>
        <Input value={username} onChange={(e) => setUsername(e.target.value)} />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-muted">Password</span>
        <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      <Button variant="primary" disabled={submitting || !username || !password} onClick={submit} className="self-start">
        {submitting ? "Signing in…" : "Sign in"}
      </Button>
      <p className="text-ink-muted text-xs">
        This signs in the browser for read pages. Pushing over Git and the write actions on this site (merge, review,
        access management) still use a personal access token &mdash; see the sign-in box on the relevant page.
      </p>
    </div>
  );
}
