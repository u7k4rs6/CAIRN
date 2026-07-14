import { api, ApiError } from "@/lib/api";
import { RepoHeader } from "@/components/RepoHeader";
import { NotFoundState } from "@/components/NotFoundState";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { CairnMark } from "@/components/CairnMark";

/** Highlights the matched query substring in --route-tint (redesign spec, section 9). Case-insensitive, matching the trigram index's own case-insensitive candidate verification. */
function highlightMatch(line: string, query: string) {
  if (!query) return line;
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const parts = line.split(new RegExp(`(${escaped})`, "gi"));
  return parts.map((part, i) =>
    part.toLowerCase() === query.toLowerCase() ? (
      <mark key={i} className="bg-route-tint text-ink rounded-sm">
        {part}
      </mark>
    ) : (
      part
    )
  );
}

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
            <Input name="q" defaultValue={query} placeholder="Search code in this repository" className="flex-1 font-mono" />
            <Button type="submit" variant="primary">
              Search
            </Button>
          </form>

          {!query && <p className="text-ink-muted text-sm">Enter a search term above.</p>}
          {response?.queryTooShort && (
            <p className="text-ink-muted text-sm">Search needs at least three characters.</p>
          )}
          {response?.indexing && (
            <p className="text-ink-muted text-sm">This repository&rsquo;s search index is still building. Try again shortly.</p>
          )}
          {response && !response.queryTooShort && !response.indexing && response.results.length === 0 && (
            <div className="contour-bg border border-hairline rounded p-8 text-center flex flex-col items-center gap-3">
              <CairnMark size={24} className="text-ink-muted" />
              <p className="text-ink-2 text-sm">
                No matches for <span className="font-mono">&quot;{query}&quot;</span>.
              </p>
            </div>
          )}
          {response?.results.map((file) => (
            <div key={file.path} className="border border-hairline rounded overflow-hidden">
              <div className="bg-surface-sunken border-b-2 border-contour px-3 py-1.5 text-sm font-mono font-medium text-ink">
                {file.path}
              </div>
              <div className="divide-y divide-hairline bg-surface">
                {file.lines.map((line) => (
                  <div key={line.lineNumber} className="flex gap-3 px-3 py-1 text-xs font-mono">
                    <span className="text-ink-muted w-10 text-right shrink-0">{line.lineNumber}</span>
                    <span className="whitespace-pre overflow-x-auto">{highlightMatch(line.line, query)}</span>
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
