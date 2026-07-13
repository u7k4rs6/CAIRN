"use client";

import { useState } from "react";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

type Line = { lineNumber: number; html: string };
type BlameLine = { lineNumber: number; line: string; commitId: string };

/**
 * Frontend spec, section 5.3: syntax highlighting, line numbers, and a
 * {@code BlameToggle}. Highlighting happens server-side (the page passes
 * pre-highlighted HTML per line); this component's own state is just the blame
 * toggle, fetched client-side on demand rather than on every blob load, since most
 * views of a file never ask "who wrote this line."
 */
export function CodeViewer({
  owner,
  repo,
  gitRef,
  path,
  lines,
}: {
  owner: string;
  repo: string;
  gitRef: string;
  path: string;
  lines: Line[];
}) {
  const [blame, setBlame] = useState<BlameLine[] | null>(null);
  const [loading, setLoading] = useState(false);

  async function toggleBlame() {
    if (blame) {
      setBlame(null);
      return;
    }
    setLoading(true);
    const res = await fetch(`${API_BASE}/api/repos/${owner}/${repo}/blame/${gitRef}/${path}`, { cache: "no-store" });
    if (res.ok) {
      setBlame(await res.json());
    }
    setLoading(false);
  }

  return (
    <div>
      <div className="flex justify-end mb-2">
        <button
          onClick={toggleBlame}
          className="text-xs border border-border rounded px-2 py-1 text-fg-muted hover:text-fg"
        >
          {loading ? "Loading blame..." : blame ? "Hide blame" : "Blame"}
        </button>
      </div>
      <div className="border border-border rounded overflow-hidden">
        <pre className="overflow-x-auto text-xs font-mono-data leading-5 m-0">
          {lines.map((line, i) => {
            const b = blame?.[i];
            const prev = blame?.[i - 1];
            const showSha = b && b.commitId !== prev?.commitId;
            return (
              <div key={line.lineNumber} className="flex">
                {blame && (
                  <span
                    className="select-none text-fg-muted w-20 shrink-0 pr-2 text-right border-r border-border"
                    title={b?.commitId}
                  >
                    {showSha ? b?.commitId.slice(0, 7) : ""}
                  </span>
                )}
                <span className="select-none text-fg-muted w-10 text-right pr-3 pl-2 shrink-0">{line.lineNumber}</span>
                <span className="whitespace-pre" dangerouslySetInnerHTML={{ __html: line.html || " " }} />
              </div>
            );
          })}
        </pre>
      </div>
    </div>
  );
}
