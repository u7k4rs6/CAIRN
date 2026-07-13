import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";

/**
 * Frontend spec, section 5.8: code search results. States: loading is implicit
 * (the whole page is server-rendered from the fetch), no results, query too short,
 * indexing (a repo whose trigram index is still building), and denied/not-found
 * masked identically like every other repo route.
 */
export default async function SearchPage({
  params,
  searchParams,
}: {
  params: Promise<{ owner: string; repo: string }>;
  searchParams: Promise<{ q?: string }>;
}) {
  const { owner, repo } = await params;
  const { q } = await searchParams;
  const query = q || "";

  try {
    const response = query ? await api.search(owner, repo, query) : null;

    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="search" />
        <div className="px-4 py-4 max-w-3xl mx-auto flex flex-col gap-4">
          <form className="flex gap-2">
            <input
              name="q"
              defaultValue={query}
              placeholder="Search code in this repository"
              className="flex-1 border border-border rounded px-3 py-2 bg-bg-subtle font-mono-data text-sm"
            />
            <button type="submit" className="bg-accent text-accent-fg rounded px-4 py-2 text-sm font-medium">
              Search
            </button>
          </form>

          {!query && <p className="text-fg-muted text-sm">Enter a search term above.</p>}
          {response?.queryTooShort && (
            <p className="text-fg-muted text-sm">Search needs at least three characters.</p>
          )}
          {response?.indexing && (
            <p className="text-fg-muted text-sm">This repository's search index is still building. Try again shortly.</p>
          )}
          {response && !response.queryTooShort && !response.indexing && response.results.length === 0 && (
            <p className="text-fg-muted text-sm">No matches for &quot;{query}&quot;.</p>
          )}
          {response?.results.map((file) => (
            <div key={file.path} className="border border-border rounded overflow-hidden">
              <div className="bg-bg-subtle px-3 py-1.5 text-sm font-mono-data font-medium">{file.path}</div>
              <div className="divide-y divide-border">
                {file.lines.map((line) => (
                  <div key={line.lineNumber} className="flex gap-3 px-3 py-1 text-xs font-mono-data">
                    <span className="text-fg-muted w-10 text-right shrink-0">{line.lineNumber}</span>
                    <span className="whitespace-pre overflow-x-auto">{line.line}</span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  } catch (e) {
    if (e instanceof ApiError) {
      return <NotFoundState label="repository" />;
    }
    throw e;
  }
}
