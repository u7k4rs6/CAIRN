"use client";

import { useState } from "react";
import { List, type RowComponentProps } from "react-window";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

/** One line, text-xs with leading-5 (20px line-height, no padding): matches react-window's fixed row height exactly. */
const ROW_HEIGHT = 20;
const MAX_VIEWPORT_HEIGHT = 600;

type Line = { lineNumber: number; html: string };
type BlameLine = { lineNumber: number; line: string; commitId: string };

type RowProps = { lines: Line[]; blame: BlameLine[] | null };

function Row({ index, style, lines, blame }: RowComponentProps<RowProps>) {
  const line = lines[index];
  const b = blame?.[index];
  const prev = blame?.[index - 1];
  const showSha = b && b.commitId !== prev?.commitId;
  return (
    <div style={style} className="flex text-xs font-mono-data">
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
}

/**
 * Frontend spec, section 5.3: syntax highlighting, line numbers, a
 * {@code BlameToggle}, and virtualized rendering ("cost is O(visible rows), not
 * O(file length)"). {@code react-window}'s {@code List} does the windowing: only
 * rows within the viewport (plus a small overscan) ever mount, so a 50k-line file
 * costs the same to render as a 50-line one. Highlighting happens server-side (the
 * page passes pre-highlighted HTML per line); this component's own state is the
 * blame toggle, fetched client-side on demand.
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

  const viewportHeight = Math.min(lines.length * ROW_HEIGHT, MAX_VIEWPORT_HEIGHT);

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
        <List
          rowComponent={Row}
          rowCount={lines.length}
          rowHeight={ROW_HEIGHT}
          rowProps={{ lines, blame }}
          defaultHeight={viewportHeight}
          style={{ height: viewportHeight }}
          className="overflow-x-auto"
        />
      </div>
    </div>
  );
}
