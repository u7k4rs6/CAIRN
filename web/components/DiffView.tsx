"use client";

import { useState } from "react";
import { List, type RowComponentProps } from "react-window";
import type { ConversationEntry, FileDiff } from "@/lib/api";
import { highlightLine, languageForPath } from "@/lib/highlight";
import { Textarea } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";
import { Avatar } from "@/components/ui/Avatar";
import { useAuth } from "@/components/AuthBar";
import { apiBase } from "@/lib/apiBase";

const API_BASE = apiBase();

type Row = { kind: "equal" | "add" | "del"; oldNo: number | null; newNo: number | null; text: string };

function rowsFor(diff: FileDiff): Row[] {
  const rows: Row[] = [];
  for (const edit of diff.edits) {
    if (edit.type === "EQUAL") {
      for (let i = 0; i < edit.origEnd - edit.origStart; i++) {
        rows.push({
          kind: "equal",
          oldNo: edit.origStart + i + 1,
          newNo: edit.revStart + i + 1,
          text: diff.newLines[edit.revStart + i] ?? "",
        });
      }
    } else if (edit.type === "DELETE") {
      for (let i = edit.origStart; i < edit.origEnd; i++) {
        rows.push({ kind: "del", oldNo: i + 1, newNo: null, text: diff.oldLines[i] ?? "" });
      }
    } else {
      for (let i = edit.revStart; i < edit.revEnd; i++) {
        rows.push({ kind: "add", oldNo: null, newNo: i + 1, text: diff.newLines[i] ?? "" });
      }
    }
  }
  return rows;
}

const ROW_HEIGHT = 20;
const MAX_VIEWPORT_HEIGHT = 480;

type DiffRowProps = {
  rows: Row[];
  language: string | null;
  commentableLines: Set<number> | null;
  onGutterClick: ((line: number) => void) | null;
};

function DiffRow({ index, style, rows, language, commentableLines, onGutterClick }: RowComponentProps<DiffRowProps>) {
  const row = rows[index];
  const bg = row.kind === "add" ? "var(--diff-add-bg)" : row.kind === "del" ? "var(--diff-del-bg)" : undefined;
  const markColor = row.kind === "add" ? "var(--diff-add-ink)" : row.kind === "del" ? "var(--diff-del-ink)" : "var(--ink-muted)";
  const canComment = onGutterClick && row.newNo !== null;
  const hasComment = commentableLines?.has(row.newNo ?? -1);
  return (
    <div style={{ ...style, background: bg }} className="group flex text-xs font-mono leading-5">
      <span className="select-none w-4 shrink-0 flex items-center justify-center">
        {canComment && (
          <button
            onClick={() => onGutterClick!(row.newNo!)}
            aria-label={`Comment on line ${row.newNo}`}
            title={`Comment on line ${row.newNo}`}
            className={`w-3.5 h-3.5 rounded-sm text-[10px] leading-none flex items-center justify-center ${
              hasComment ? "bg-route text-on-route opacity-100" : "bg-route-tint text-route-ink opacity-0 group-hover:opacity-100"
            }`}
          >
            +
          </button>
        )}
      </span>
      <span className="select-none text-right pr-2 text-ink-muted w-10 border-r border-hairline shrink-0">{row.oldNo ?? ""}</span>
      <span className="select-none text-right pr-2 text-ink-muted w-10 border-r border-hairline shrink-0">{row.newNo ?? ""}</span>
      <span className="pl-2 whitespace-pre">
        <span style={{ color: markColor }} className="select-none">
          {row.kind === "add" ? "+" : row.kind === "del" ? "-" : " "}
        </span>
        <span dangerouslySetInnerHTML={{ __html: highlightLine(row.text, language) }} />
      </span>
    </div>
  );
}

/** A legend of touched files with +/- counts in mono (redesign spec, section 8). */
function FilesChangedLegend({ diffs }: { diffs: FileDiff[] }) {
  return (
    <ul className="border border-hairline rounded overflow-hidden divide-y divide-hairline bg-surface text-sm mb-4">
      {diffs.map((diff) => {
        const rows = rowsFor(diff);
        const added = rows.filter((r) => r.kind === "add").length;
        const removed = rows.filter((r) => r.kind === "del").length;
        return (
          <li key={diff.path} className="flex items-center gap-2 px-3 py-1.5">
            <span
              className={`font-mono text-xs w-16 shrink-0 ${
                diff.kind === "ADDED" ? "text-veg" : diff.kind === "DELETED" ? "text-survey-red" : "text-ink-muted"
              }`}
            >
              {diff.kind === "ADDED" ? "added" : diff.kind === "DELETED" ? "deleted" : "modified"}
            </span>
            <span className="font-mono truncate min-w-0">{diff.path}</span>
            <span className="font-mono text-xs ml-auto shrink-0 whitespace-nowrap">
              {added > 0 && <span className="text-diff-add-ink">+{added}</span>}
              {added > 0 && removed > 0 && " "}
              {removed > 0 && <span className="text-diff-del-ink">-{removed}</span>}
            </span>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * One file's line-comment thread below its diff, plus the active compose box
 * (FR-COLLAB-3). Deliberately not interleaved inside the virtualized row list
 * itself: `react-window`'s `List` here uses a fixed row height, and a comment
 * thread's height varies with its content, so inserting it as a row would either
 * break the fixed-height assumption or require measuring rendered heights per
 * comment - real complexity this session doesn't take on. The gutter "+" (in
 * `DiffRow`) is still exactly where the affordance the spec asks for lives; only
 * the resulting thread renders here, clearly labeled by line number, instead of
 * inline in the virtualized list.
 */
function LineCommentThread({
  owner,
  repo,
  prNumber,
  path,
  comments,
  activeLine,
  onClose,
  onPosted,
}: {
  owner: string;
  repo: string;
  prNumber: number;
  path: string;
  comments: ConversationEntry[];
  activeLine: number | null;
  onClose: () => void;
  onPosted: (entry: ConversationEntry) => void;
}) {
  const { isAuthenticated, authHeader } = useAuth();
  const [body, setBody] = useState("");
  const [posting, setPosting] = useState(false);

  const byLine = new Map<number, ConversationEntry[]>();
  for (const c of comments) {
    if (c.line === null) continue;
    byLine.set(c.line, [...(byLine.get(c.line) ?? []), c]);
  }
  const lines = [...byLine.keys()].sort((a, b) => a - b);

  async function submit() {
    if (!body.trim() || activeLine === null) return;
    setPosting(true);
    const res = await fetch(`${API_BASE}/api/repos/${owner}/${repo}/pulls/${prNumber}/reviews`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...authHeader },
      body: JSON.stringify({ verdict: "COMMENT", body, path, line: activeLine }),
    });
    setPosting(false);
    if (res.ok) {
      const saved = await res.json();
      onPosted({
        kind: "REVIEW",
        id: saved.id,
        author: saved.reviewer?.username ?? "you",
        verdict: "COMMENT",
        body,
        path,
        line: activeLine,
      });
      setBody("");
      onClose();
    }
  }

  if (lines.length === 0 && activeLine === null) {
    return null;
  }

  return (
    <div className="border-t border-hairline bg-surface px-3 py-2 flex flex-col gap-2 text-sm">
      {lines.map((line) => (
        <div key={line} className="flex flex-col gap-1.5">
          <div className="font-mono text-xs text-ink-muted">line {line}</div>
          {byLine.get(line)!.map((c) => (
            <div key={c.id} className="flex items-start gap-2">
              <Avatar username={c.author} size={16} />
              <p className="text-ink-2">
                <span className="font-medium text-ink">{c.author}</span> {c.body}
              </p>
            </div>
          ))}
        </div>
      ))}
      {activeLine !== null && (
        <div className="flex flex-col gap-1.5 border border-hairline rounded p-2">
          <div className="font-mono text-xs text-ink-muted">Commenting on line {activeLine}</div>
          <Textarea value={body} onChange={(e) => setBody(e.target.value)} rows={2} placeholder="Leave a comment on this line" />
          <div className="flex gap-2 self-start">
            <Button variant="primary" onClick={submit} disabled={posting || !body.trim()}>
              {posting ? "Posting…" : "Comment"}
            </Button>
            <Button variant="secondary" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

export function DiffView({
  diffs,
  owner,
  repo,
  prNumber,
  lineComments,
}: {
  diffs: FileDiff[];
  owner?: string;
  repo?: string;
  prNumber?: number;
  lineComments?: ConversationEntry[];
}) {
  const { isAuthenticated } = useAuth();
  const [posted, setPosted] = useState<ConversationEntry[]>([]);
  const [activeByPath, setActiveByPath] = useState<Record<string, number | null>>({});
  // hasPrContext: enough to show existing line comments read-only, and to mount
  // the thread panel at all. commentingEnabled additionally requires sign-in - an
  // unauthenticated visitor would otherwise hit a gutter affordance that can never
  // successfully submit (the write endpoint requires a principal), same as
  // ReviewComposer/CommentComposer simply not rendering their compose UI when
  // signed out.
  const hasPrContext = Boolean(owner && repo && prNumber);
  const commentingEnabled = hasPrContext && isAuthenticated;
  const allLineComments = [...(lineComments ?? []), ...posted];

  if (diffs.length === 0) {
    return <p className="text-ink-muted text-sm px-4 py-8">No changes in this diff.</p>;
  }
  return (
    <div className="flex flex-col gap-4">
      <FilesChangedLegend diffs={diffs} />
      {diffs.map((diff) => {
        const language = languageForPath(diff.path);
        const rows = rowsFor(diff);
        const viewportHeight = Math.min(rows.length * ROW_HEIGHT, MAX_VIEWPORT_HEIGHT);
        const commentsForFile = allLineComments.filter((c) => c.path === diff.path);
        const commentableLines = hasPrContext ? new Set(commentsForFile.map((c) => c.line!)) : null;
        const activeLine = activeByPath[diff.path] ?? null;
        return (
          <div key={diff.path} className="border border-hairline rounded overflow-hidden">
            <div className="bg-surface-sunken border-b-2 border-contour px-3 py-1.5 text-sm font-mono flex items-center gap-2">
              <span
                className={
                  diff.kind === "ADDED" ? "text-veg" : diff.kind === "DELETED" ? "text-survey-red" : "text-ink-muted"
                }
              >
                {diff.kind === "ADDED" ? "added" : diff.kind === "DELETED" ? "deleted" : "modified"}
              </span>
              <span className="text-ink">{diff.path}</span>
            </div>
            <List
              rowComponent={DiffRow}
              rowCount={rows.length}
              rowHeight={ROW_HEIGHT}
              rowProps={{
                rows,
                language,
                commentableLines,
                onGutterClick: commentingEnabled
                  ? (line: number) => setActiveByPath((prev) => ({ ...prev, [diff.path]: prev[diff.path] === line ? null : line }))
                  : null,
              }}
              defaultHeight={viewportHeight}
              style={{ height: viewportHeight }}
              className="overflow-x-auto"
            />
            {hasPrContext && (
              <LineCommentThread
                owner={owner!}
                repo={repo!}
                prNumber={prNumber!}
                path={diff.path}
                comments={commentsForFile}
                activeLine={activeLine}
                onClose={() => setActiveByPath((prev) => ({ ...prev, [diff.path]: null }))}
                onPosted={(entry) => setPosted((prev) => [...prev, entry])}
              />
            )}
          </div>
        );
      })}
    </div>
  );
}
