import Link from "next/link";
import type { CommitView } from "@/lib/api";
import { Avatar } from "@/components/ui/Avatar";

export function CommitRow({ owner, repo, commit }: { owner: string; repo: string; commit: CommitView }) {
  const firstLine = commit.message.split("\n")[0];
  const date = new Date(commit.authorTime * 1000).toISOString().slice(0, 10);
  return (
    <div className="flex items-center gap-3 px-3 py-2 border-b border-hairline last:border-0 text-sm">
      <Avatar username={commit.authorName} />
      <div className="min-w-0 flex-1">
        <Link href={`/${owner}/${repo}/commit/${commit.id}`} className="hover:text-route hover:underline font-medium truncate block text-ink">
          {firstLine}
        </Link>
        <span className="text-ink-muted text-xs">
          {commit.authorName} committed on <span className="font-mono">{date}</span>
        </span>
      </div>
      <Link href={`/${owner}/${repo}/commit/${commit.id}`} className="font-mono text-xs text-ink-muted shrink-0 ml-4">
        {commit.id.slice(0, 7)}
      </Link>
    </div>
  );
}
