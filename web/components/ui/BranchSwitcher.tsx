"use client";

import { useRouter } from "next/navigation";
import type { Branch } from "@/lib/api";

/**
 * A real branch switcher (redesign spec, section 7's "main &#9662;"), now that
 * `GET /branches` exists. Navigates to this same repo's tree view at the chosen
 * branch - the one route every branch can always render, unlike issues/pulls/search
 * which aren't ref-scoped.
 */
export function BranchSwitcher({
  owner,
  repo,
  current,
  branches,
}: {
  owner: string;
  repo: string;
  current: string;
  branches: Branch[];
}) {
  const router = useRouter();

  if (branches.length === 0) {
    return <span className="font-mono text-ink-2 border border-hairline rounded px-2 py-1">{current}</span>;
  }

  return (
    <select
      value={branches.some((b) => b.name === current) ? current : ""}
      onChange={(e) => router.push(`/${owner}/${repo}/tree/${e.target.value}`)}
      aria-label="Switch branch"
      className="font-mono text-ink-2 border border-hairline rounded px-2 py-1 bg-surface"
    >
      {!branches.some((b) => b.name === current) && <option value="">{current}</option>}
      {branches.map((b) => (
        <option key={b.name} value={b.name}>
          {b.name}
        </option>
      ))}
    </select>
  );
}
