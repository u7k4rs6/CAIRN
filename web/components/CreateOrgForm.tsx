"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/AuthBar";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

export function CreateOrgForm() {
  const { isAuthenticated, authHeader } = useAuth();
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const router = useRouter();

  if (!isAuthenticated) {
    return <p className="text-ink-muted text-sm">Sign in above to create an organization.</p>;
  }

  async function create() {
    setError(null);
    const res = await fetch(`${API_BASE}/api/orgs`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ name }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: `request failed: ${res.status}` }));
      setError(body.error || `request failed: ${res.status}`);
      return;
    }
    router.push(`/orgs/${name}/teams`);
  }

  return (
    <div className="flex flex-col gap-2">
      {error && <div className="border border-survey-red text-survey-red text-sm rounded p-2">{error}</div>}
      <div className="flex items-end gap-2">
        <label className="flex flex-col gap-1 flex-1">
          <span className="text-xs text-ink-muted">Organization name</span>
          <Input value={name} onChange={(e) => setName(e.target.value)} className="font-mono" />
        </label>
        <Button variant="primary" onClick={create}>
          Create organization
        </Button>
      </div>
    </div>
  );
}
