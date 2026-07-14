"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { parseOwnerRepoPath } from "@/lib/repoPath";

/** The top-bar "jump to a repository" box - same relative-navigation rule as {@link BrowseForm}. */
export function GotoSearchBox() {
  const [value, setValue] = useState("");
  const router = useRouter();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const path = parseOwnerRepoPath(value);
    if (path) {
      router.push(`/${path}`);
    }
  }

  return (
    <form onSubmit={submit} className="flex-1 max-w-md">
      <input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="search repos, refs&hellip; (owner/repo)"
        aria-label="Jump to a repository"
        className="w-full font-mono text-sm border border-hairline rounded bg-surface-sunken px-3 py-1.5 text-ink placeholder:text-ink-muted focus:border-route"
      />
    </form>
  );
}
