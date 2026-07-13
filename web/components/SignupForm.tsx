"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * Frontend spec route {@code /signup} (section 3). There is no session/cookie login
 * in this app yet (P2 gap-closure item), so signup immediately mints a personal
 * access token with the just-chosen password over Basic auth (the one endpoint
 * that authenticates with a real password, see {@code AccountController}) and saves
 * it exactly where {@link @/components/AuthBar} looks for one, so a brand-new
 * visitor lands signed in with no separate "now go find your token" step.
 */
export function SignupForm() {
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const router = useRouter();

  async function submit() {
    setSubmitting(true);
    setError(null);
    const signupRes = await fetch(`${API_BASE}/api/users`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, email, password }),
    });
    if (!signupRes.ok) {
      const body = await signupRes.json().catch(() => ({ error: `signup failed: ${signupRes.status}` }));
      setError(body.error || `signup failed: ${signupRes.status}`);
      setSubmitting(false);
      return;
    }
    const tokenRes = await fetch(`${API_BASE}/api/users/${username}/tokens`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: "Basic " + btoa(`${username}:${password}`) },
      body: JSON.stringify({}),
    });
    if (!tokenRes.ok) {
      setError("account created, but minting a token failed - sign in manually with your password once session login exists");
      setSubmitting(false);
      return;
    }
    const { token } = await tokenRes.json();
    localStorage.setItem("cairn-username", username);
    localStorage.setItem("cairn-token", token);
    router.push(`/`);
  }

  return (
    <div className="flex flex-col gap-3 max-w-sm">
      {error && <div className="border border-danger text-danger text-sm rounded p-2">{error}</div>}
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Username</span>
        <input value={username} onChange={(e) => setUsername(e.target.value)} className="border border-border rounded px-2 py-1.5 bg-bg text-sm" />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Email</span>
        <input value={email} onChange={(e) => setEmail(e.target.value)} className="border border-border rounded px-2 py-1.5 bg-bg text-sm" />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Password</span>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} className="border border-border rounded px-2 py-1.5 bg-bg text-sm" />
      </label>
      <button
        disabled={submitting || !username || !password}
        onClick={submit}
        className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium disabled:opacity-50 self-start"
      >
        {submitting ? "Creating account..." : "Create account"}
      </button>
    </div>
  );
}
