import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";

export default async function BlobPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string; ref: string; path: string[] }>;
}) {
  const { owner, repo, ref, path } = await params;

  try {
    const blob = await api.blob(owner, repo, ref, path);
    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" />
        <div className="px-4 py-4 max-w-4xl mx-auto">
          <div className="text-sm text-fg-muted mb-2 font-mono-data">{path.join("/")}</div>
          <div className="border border-border rounded overflow-hidden">
            <pre className="overflow-x-auto text-xs font-mono-data p-3 leading-5">
              {blob.content.split("\n").map((line, i) => (
                <div key={i} className="flex">
                  <span className="select-none text-fg-muted w-10 text-right pr-3 shrink-0">{i + 1}</span>
                  <span className="whitespace-pre">{line}</span>
                </div>
              ))}
            </pre>
          </div>
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="file" />;
    }
    throw e;
  }
}
