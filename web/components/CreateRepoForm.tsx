"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthBar";
import { Input, Select } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { apiBase } from "@/lib/apiBase";

const API_BASE = apiBase();

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
    return <p className="text-ink-muted text-sm">Sign in above to create a repository.</p>;
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
      {error && <div className="border border-survey-red text-survey-red text-sm rounded p-2">{error}</div>}
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-muted">Owner</span>
        <Input value={org} onChange={(e) => setOrg(e.target.value)} placeholder={username || "(you)"} className="font-mono" />
        <span className="text-xs text-ink-muted">Leave blank to own it yourself, or name an organization you administer.</span>
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-muted">Repository name</span>
        <Input value={name} onChange={(e) => setName(e.target.value)} className="font-mono" />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-ink-muted">Visibility</span>
        <Select value={visibility} onChange={(e) => setVisibility(e.target.value as typeof visibility)}>
          <option value="PUBLIC">Public</option>
          <option value="INTERNAL">Internal</option>
          <option value="PRIVATE">Private</option>
        </Select>
      </label>
      <Button variant="primary" onClick={create} disabled={!name} className="self-start">
        Create repository
      </Button>
    </div>
  );
}
