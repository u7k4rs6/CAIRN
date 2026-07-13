"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthBar";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * FR-REPO-1, previously reachable only via {@code curl} against
 * {@code POST /api/repos}: M8 built browsing, issues, and pull requests but never a
 * "create a repository" screen, so a user wanting anything beyond auto-vivify-on-push
 * or a hand-written HTTP request had no UI path at all.
 */
export function CreateRepoForm() {
  const { isAuthenticated, username, authHeader } = useAuth();
  const [name, setName] = useState("");
  const [visibility, setVisibility] = useState<"PUBLIC" | "INTERNAL" | "PRIVATE">("PRIVATE");
  const [org, setOrg] = useState("");
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  if (!isAuthenticated) {
    return <p className="text-fg-muted text-sm">Sign in above to create a repository.</p>;
  }

  async function create() {
    setError(null);
    const res = await fetch(`${API_BASE}/api/repos`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ name, visibility, org: org || null }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `request failed: ${res.status}` }));
      setError(body.error || `request failed: ${res.status}`);
      return;
    }
    const created = await res.json();
    router.push(`/${created.owner}/${created.name}`);
  }

  return (
    <div className="flex flex-col gap-3 max-w-sm">
      {error && <div className="border border-danger text-danger text-sm rounded p-2">{error}</div>}
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Owner</span>
        <input value={org} onChange={(e) => setOrg(e.target.value)} placeholder={username || "(you)"} className="border border-border rounded px-2 py-1.5 bg-bg text-sm" />
        <span className="text-xs text-fg-muted">Leave blank to own it yourself, or name an organization you administer.</span>
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Repository name</span>
        <input value={name} onChange={(e) => setName(e.target.value)} className="border border-border rounded px-2 py-1.5 bg-bg text-sm" />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Visibility</span>
        <select value={visibility} onChange={(e) => setVisibility(e.target.value as typeof visibility)} className="border border-border rounded px-2 py-1.5 bg-bg text-sm">
          <option value="PUBLIC">Public</option>
          <option value="INTERNAL">Internal</option>
          <option value="PRIVATE">Private</option>
        </select>
      </label>
      <button onClick={create} disabled={!name} className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium disabled:opacity-50 self-start">
        Create repository
      </button>
    </div>
  );
}
