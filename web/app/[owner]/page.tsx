import Link from "next/link";
import { api } from "@/lib/api";
import { CairnMark } from "@/components/CairnMark";
import { StateBadge } from "@/components/StateBadge";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * The `/{owner}` profile page (redesign spec, section 9, item 9): fixes the 404
 * the repo header's own owner breadcrumb link has pointed at since M8. Now backed
 * by `GET /api/repos/{owner}` (this session's endpoint), filtered server-side to
 * what the requester may see - a private repo among the owner's repos simply isn't
 * in the response, the same masking every other repo-scoped read in this app uses.
 */
export default async function OwnerProfilePage({ params }: { params: Promise<{ owner: string }> }) {
  const { owner } = await params;
  const [orgRes, repositories] = await Promise.all([
    fetch(`${API_BASE}/api/orgs/${owner}`, { cache: "no-store" }).catch(() => null),
    api.ownerRepos(owner),
  ]);
  const isOrg = orgRes?.ok ?? false;

  return (
    <div className="max-w-2xl mx-auto px-4 py-16 flex flex-col items-center gap-3">
      <CairnMark size={40} className="text-route" />
      <h1 className="text-xl font-display font-bold text-ink">{owner}</h1>
      <p className="text-ink-muted text-sm">{isOrg ? "Organization" : "Owner"}</p>
      <div className="border-t border-contour w-full max-w-sm my-2" />

      {repositories.length === 0 ? (
        <p className="text-ink-2 text-sm max-w-sm text-center">
          No visible repositories for {owner} yet. Use the search box above to jump straight to one, e.g.{" "}
          <code className="font-mono text-xs bg-surface-sunken px-1 py-0.5 rounded">{owner}/repo-name</code>.
        </p>
      ) : (
        <ul className="w-full max-w-lg border border-hairline rounded overflow-hidden divide-y divide-hairline">
          {repositories.map((repo) => (
            <li key={repo.id} className="flex items-center justify-between px-3 py-2 text-sm bg-surface">
              <Link href={`/${owner}/${repo.name}`} className="font-mono text-ink hover:text-route hover:underline">
                {owner}/{repo.name}
              </Link>
              <StateBadge state={repo.visibility} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
