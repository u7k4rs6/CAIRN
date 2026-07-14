import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { FileTreeView } from "@/components/FileTreeView";
import { NotFoundState } from "@/components/NotFoundState";

export default async function TreePage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; ref: string; path?: string[] }>;
}) {
  const { owner, repo, ref, path = [] } = await params;

  try {
    const entries = await api.tree(owner, repo, ref, path);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" gitRef={ref} />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <div className="text-sm text-ink-muted mb-2 font-mono">
            <Link href={`/${owner}/${repo}`} className="hover:text-route hover:underline">
              {repo}
            </Link>
            {path.map((segment, i) => (
              <span key={i}>
                <span className="mx-1 text-contour">&#9656;</span>
                <Link
                  href={`/${owner}/${repo}/tree/${ref}/${path.slice(0, i + 1).join("/")}`}
                  className="hover:text-route hover:underline"
                >
                  {segment}
                </Link>
              </span>
            ))}
          </div>
          <div className="border border-hairline rounded overflow-hidden">
            <FileTreeView owner={owner} repo={repo} gitRef={ref} path={path} entries={entries} />
          </div>
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="path" />;
    }
    throw e;
  }
}
