import Link from "next/link";
import { SignageTabs } from "@/components/ui/SignageTabs";
import { CopyButton } from "@/components/ui/CopyButton";
import { BranchSwitcher } from "@/components/ui/BranchSwitcher";
import type { Branch } from "@/lib/api";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/**
 * The repo header (redesign spec, section 7): the owner/repo breadcrumb reads as a
 * route (mono, waypoint separators), and the clone URL is copyable. `branches`,
 * when passed, turns the ref label into a real switcher (`GET /branches`, this
 * session's endpoint); callers that don't have a branch list yet (every page but
 * repo home) still get the previous static mono label, unchanged.
 */
export function RepoHeader({
  owner,
  repo,
  active,
  gitRef = "main",
  branches = [],
}: {
  owner: string;
  repo: string;
  active: string;
  gitRef?: string;
  branches?: Branch[];
}) {
  const tabs = [
    { key: "code", label: "Code", href: `/${owner}/${repo}` },
    { key: "issues", label: "Issues", href: `/${owner}/${repo}/issues` },
    { key: "pulls", label: "Pull requests", href: `/${owner}/${repo}/pulls` },
    { key: "search", label: "Search", href: `/${owner}/${repo}/search` },
    // Not gated on the current user's role in the tab bar itself: this app has no
    // server-side session, so a Server Component rendering this header cannot know
    // the browser's effective role (see AccessSettingsPanel's doc comment). The
    // destination page itself applies the real gate, masked the same way every
    // other admin/read check in this app already is (security doc, section 6.3).
    { key: "settings", label: "Settings", href: `/${owner}/${repo}/settings/access` },
  ];
  const cloneUrl = `${API_BASE}/${owner}/${repo}.git`;

  return (
    <div className="border-b border-hairline px-4 pt-3 bg-surface">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
        <div className="font-mono text-sm text-ink-muted min-w-0 truncate">
          <Link href={`/${owner}`} className="hover:text-route hover:underline">
            {owner}
          </Link>
          <span className="mx-1.5 text-contour">&#9656;</span>
          <Link href={`/${owner}/${repo}`} className="font-semibold text-ink hover:text-route hover:underline">
            {repo}
          </Link>
        </div>
        <div className="flex items-center gap-2 text-sm shrink-0">
          <BranchSwitcher owner={owner} repo={repo} current={gitRef} branches={branches} />
          <span className="font-mono text-xs text-ink-muted border border-hairline rounded px-2 py-1 max-w-[16rem] truncate" title={cloneUrl}>
            {cloneUrl}
          </span>
          <CopyButton value={cloneUrl} label="Copy clone URL" />
        </div>
      </div>
      <SignageTabs tabs={tabs} active={active} />
    </div>
  );
}
