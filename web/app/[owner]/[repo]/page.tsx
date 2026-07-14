import { api, ApiError } from "@/lib/api";
import { publicGitBase } from "@/lib/apiBase";
import { RepoHeader } from "@/components/RepoHeader";
import { FileTreeView } from "@/components/FileTreeView";
import { NotFoundState } from "@/components/NotFoundState";
import { RouteGraph } from "@/components/RouteGraph";
import { CopyButton } from "@/components/ui/CopyButton";
import { CairnMark } from "@/components/CairnMark";

const DEFAULT_REF = "main";
const TEASER_COMMIT_COUNT = 6;

/** A stable color per language name, drawn from the map-legend palette (redesign spec, section 5) rather than one fixed accent per language. */
const LANGUAGE_PALETTE = ["var(--route)", "var(--water)", "var(--veg)", "var(--caution)", "var(--route-ink)"];
function languageColor(lang: string): string {
  let hash = 0;
  for (let i = 0; i < lang.length; i++) {
    hash = (hash * 31 + lang.charCodeAt(i)) | 0;
  }
  return LANGUAGE_PALETTE[Math.abs(hash) % LANGUAGE_PALETTE.length];
}

export default async function RepoHomePage({
  params,
}: {
  params: Promise<{ owner: string; repo: string }>;
}) {
  const { owner, repo } = await params;

  try {
    const entries = await api.tree(owner, repo, DEFAULT_REF, []);
    const readme = entries.find((e) => e.kind === "blob" && /^readme(\.md)?$/i.test(e.name));
    const [readmeContent, commits, branches, stats] = await Promise.all([
      readme ? api.blob(owner, repo, DEFAULT_REF, [readme.name]) : Promise.resolve(null),
      api.commits(owner, repo, DEFAULT_REF).catch(() => []),
      api.branches(owner, repo).catch(() => []),
      api.stats(owner, repo).catch(() => null),
    ]);
    const cloneUrl = `${publicGitBase()}/${owner}/${repo}.git`;
    const languageTotal = stats ? Object.values(stats.languages).reduce((a, b) => a + b, 0) : 0;
    const languages = stats
      ? Object.entries(stats.languages)
          .sort(([, a], [, b]) => b - a)
          .slice(0, 4)
          .map(([lang, bytes]) => ({ lang, pct: languageTotal > 0 ? Math.round((bytes / languageTotal) * 100) : 0 }))
      : [];

    return (
      <div>
        <RepoHeader owner={owner} repo={repo} active="code" gitRef={DEFAULT_REF} branches={branches} />
        <div className="px-4 py-4 max-w-5xl mx-auto grid grid-cols-1 lg:grid-cols-[1fr_260px] gap-6">
          <div className="min-w-0 flex flex-col gap-4">
            {entries.length === 0 ? (
              <div className="contour-bg border border-hairline rounded p-8 text-center flex flex-col items-center gap-3">
                <CairnMark size={32} className="text-ink-muted" />
                <p className="text-ink-2 text-sm max-w-sm">
                  This repository has no commits yet. Push some code to start the route:
                </p>
                <pre className="bg-surface-sunken p-3 rounded font-mono text-xs overflow-x-auto text-left w-full max-w-md">
{`git remote add origin ${cloneUrl}
git push origin main`}
                </pre>
              </div>
            ) : (
              <>
                <section aria-labelledby="route-heading">
                  <h2 id="route-heading" className="font-mono text-xs uppercase tracking-wide text-ink-muted mb-2">
                    Route
                  </h2>
                  <div className="border border-hairline rounded p-3 bg-surface">
                    <RouteGraph owner={owner} repo={repo} commits={commits.slice(0, TEASER_COMMIT_COUNT)} />
                    {commits.length > TEASER_COMMIT_COUNT && (
                      <a
                        href={`/${owner}/${repo}/commits/${DEFAULT_REF}`}
                        className="block text-xs text-ink-muted hover:text-route mt-2 pt-2 border-t border-hairline"
                      >
                        View all {commits.length} waypoints &rarr;
                      </a>
                    )}
                  </div>
                </section>
                <section aria-labelledby="files-heading">
                  <h2 id="files-heading" className="sr-only">
                    Files
                  </h2>
                  <div className="border border-hairline rounded overflow-hidden">
                    <FileTreeView owner={owner} repo={repo} gitRef={DEFAULT_REF} path={[]} entries={entries} />
                  </div>
                </section>
              </>
            )}
            {readmeContent && (
              <section className="border border-hairline rounded p-4 bg-surface">
                <pre className="whitespace-pre-wrap font-mono text-sm text-ink">{readmeContent.content}</pre>
              </section>
            )}
          </div>

          <aside className="flex flex-col gap-4">
            <div className="border border-hairline rounded p-3 bg-surface flex flex-col gap-3 text-sm">
              <h2 className="font-mono text-xs uppercase tracking-wide text-ink-muted">About</h2>
              <div className="border-t border-contour" />
              {languages.length > 0 && (
                <div className="flex flex-col gap-1.5">
                  <div className="flex h-1.5 rounded-full overflow-hidden bg-surface-sunken" aria-hidden="true">
                    {languages.map(({ lang, pct }) => (
                      <span key={lang} style={{ width: `${pct}%`, backgroundColor: languageColor(lang) }} />
                    ))}
                  </div>
                  <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-2">
                    {languages.map(({ lang, pct }) => (
                      <span key={lang} className="inline-flex items-center gap-1">
                        <span
                          className="w-2 h-2 rounded-full inline-block"
                          style={{ backgroundColor: languageColor(lang) }}
                          aria-hidden="true"
                        />
                        {lang} {pct}%
                      </span>
                    ))}
                  </div>
                </div>
              )}
              <div className="text-ink-2 font-mono text-xs">
                {(stats?.commitCount ?? commits.length)} waypoint{(stats?.commitCount ?? commits.length) === 1 ? "" : "s"}
                {" · "}
                {stats ? `${stats.branchCount} branch${stats.branchCount === 1 ? "" : "es"}` : `${branches.length} branches`}
              </div>
              <div className="flex items-center gap-1.5 min-w-0">
                <span className="font-mono text-xs text-ink-muted truncate" title={cloneUrl}>
                  {cloneUrl}
                </span>
                <CopyButton value={cloneUrl} label="Copy clone URL" />
              </div>
            </div>
          </aside>
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
