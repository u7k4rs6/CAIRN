"use client";

import { useEffect, useState } from "react";
import { List, type RowComponentProps } from "react-window";
import { Button } from "@/components/ui/Button";
import { apiBase } from "@/lib/apiBase";

const API_BASE = apiBase();

/** One line, text-xs with leading-5 (20px line-height, no padding): matches react-window's fixed row height exactly. */
const ROW_HEIGHT = 20;
const MAX_VIEWPORT_HEIGHT = 600;

type Line = { lineNumber: number; html: string };
type BlameLine = { lineNumber: number; line: string; commitId: string };

type RowProps = { lines: Line[]; blame: BlameLine[] | null; ageForCommit: Record<string, number> };

/** 0 (newest touched commit in this file) to 1 (oldest) - older reads fainter, per the redesign spec's blame-gutter rule. */
function ageOpacity(age: number | undefined): number {
  if (age === undefined) return 1;
  return 1 - age * 0.55;
}

function Row({ index, style, lines, blame, ageForCommit }: RowComponentProps<RowProps>) {
  const line = lines[index];
  const b = blame?.[index];
  const prev = blame?.[index - 1];
  const showSha = b && b.commitId !== prev?.commitId;
  return (
    <div style={style} className="flex text-xs font-mono leading-5">
      {blame && (
        <span
          className="select-none w-20 shrink-0 pr-2 text-right border-r border-hairline truncate"
          style={{ opacity: showSha ? ageOpacity(ageForCommit[b!.commitId]) : 0, color: "var(--ink-muted)" }}
          title={b?.commitId}
        >
          {b?.commitId.slice(0, 7)}
        </span>
      )}
      <span className="select-none text-ink-muted w-10 text-right pr-3 pl-2 shrink-0">{line.lineNumber}</span>
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
  const [ageForCommit, setAgeForCommit] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);

  // Blame's own response carries no timestamp (checked before building this - the
  // API's BlameLineView is {lineNumber, line, commitId} only), so "older = fainter"
  // is derived here from each distinct commit's real authorTime via the existing
  // per-commit endpoint (already used by the commit/diff page) - one request per
  // distinct commit touching this file, not per line, and only once blame is on.
  useEffect(() => {
    if (!blame) return;
    const uniqueIds = [...new Set(blame.map((b) => b.commitId))];
    let cancelled = false;
    Promise.all(
      uniqueIds.map(async (id) => {
        const res = await fetch(`${API_BASE}/api/repos/${owner}/${repo}/commit/${id}`, { cache: "force-cache" });
        if (!res.ok) return [id, null] as const;
        const data = await res.json();
        return [id, data.commit.authorTime as number] as const;
      })
    ).then((entries) => {
      if (cancelled) return;
      const valid = entries.filter((e): e is [string, number] => e[1] !== null);
      if (valid.length === 0) return;
      const times = valid.map(([, t]) => t);
      const min = Math.min(...times);
      const max = Math.max(...times);
      const span = max - min || 1;
      const ages: Record<string, number> = {};
      for (const [id, t] of valid) {
        ages[id] = (max - t) / span;
      }
      setAgeForCommit(ages);
    });
    return () => {
      cancelled = true;
    };
  }, [blame, owner, repo]);

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
        <Button variant="secondary" onClick={toggleBlame} className="text-xs py-1">
          {loading ? "Loading blame…" : blame ? "Hide blame" : "Blame"}
        </Button>
      </div>
      <div className="border border-hairline rounded overflow-hidden bg-surface">
        <List
          rowComponent={Row}
          rowCount={lines.length}
          rowHeight={ROW_HEIGHT}
          rowProps={{ lines, blame, ageForCommit }}
          defaultHeight={viewportHeight}
          style={{ height: viewportHeight }}
          className="overflow-x-auto"
        />
      </div>
    </div>
  );
}
