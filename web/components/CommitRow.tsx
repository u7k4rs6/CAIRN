import Link from "next/link";
import type { CommitView } from "@/lib/api";

export function CommitRow({ owner, repo, commit }: { owner: string; repo: string; commit: CommitView }) {
  const firstLine = commit.message.split("\n")[0];
  const date = new Date(commit.authorTime * 1000).toISOString().slice(0, 10);
  return (
    <div className="flex items-center justify-between px-3 py-2 border-b border-border last:border-0 text-sm">
      <div className="min-w-0">
        <Link href={`/${owner}/${repo}/commit/${commit.id}`} className="hover:underline font-medium truncate block">
          {firstLine}
        </Link>
        <span className="text-fg-muted text-xs">
          {commit.authorName} committed on {date}
        </span>
      </div>
      <Link href={`/${owner}/${repo}/commit/${commit.id}`} className="font-mono-data text-xs text-fg-muted shrink-0 ml-4">
        {commit.id.slice(0, 7)}
      </Link>
    </div>
  );
}
