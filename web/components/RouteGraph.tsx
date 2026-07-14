"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import type { CommitView } from "@/lib/api";
import { buildRouteGraph } from "@/lib/commitGraph";

const ROW_HEIGHT = 36;
const LANE_WIDTH = 18;
const LANE_MARGIN = 12;
const NODE_R = 4.5;

/** Traces just the default-branch (lane 0) spine over ~600ms on mount; instant under reduced-motion (globals.css). */
function useRouteTrace(rowCount: number) {
  const pathRef = useRef<SVGPathElement | null>(null);
  useEffect(() => {
    const el = pathRef.current;
    if (!el) return;
    const length = el.getTotalLength();
    el.style.setProperty("--route-length", String(length));
    // Restart the CSS animation now that the real length is known (it may have
    // already run once against the CSS fallback length before this effect fired).
    el.style.animation = "none";
    void el.getBoundingClientRect();
    el.style.animation = "";
  }, [rowCount]);
  return pathRef;
}

export function RouteGraph({ owner, repo, commits }: { owner: string; repo: string; commits: CommitView[] }) {
  const { rows, laneCount } = buildRouteGraph(commits);
  const spinePath = useRouteTrace(rows.length);

  if (rows.length === 0) {
    return <p className="text-ink-muted text-sm py-6">No waypoints yet - this branch has no commits.</p>;
  }

  const width = LANE_MARGIN * 2 + laneCount * LANE_WIDTH;
  const height = rows.length * ROW_HEIGHT;
  const xFor = (lane: number) => LANE_MARGIN + lane * LANE_WIDTH;
  const yFor = (row: number) => row * ROW_HEIGHT + ROW_HEIGHT / 2;

  const spineSegments: string[] = [];
  rows.forEach((row, i) => {
    if (row.node.lane === 0) {
      spineSegments.push(`${i === 0 ? "M" : "L"} ${xFor(0)} ${yFor(i)}`);
    }
    for (const edge of row.edgesDown) {
      if (edge.fromLane === 0 && edge.toLane === 0) {
        spineSegments.push(`L ${xFor(0)} ${yFor(i + 1)}`);
      }
    }
  });

  return (
    <div className="contour-bg flex flex-col" role="img" aria-label={`Commit route graph for ${owner}/${repo}, ${rows.length} waypoints shown`}>
      <div className="flex">
        <svg width={width} height={height} className="shrink-0" aria-hidden="true">
          {spineSegments.length > 0 && (
            <path
              ref={spinePath}
              d={spineSegments.join(" ")}
              fill="none"
              stroke="var(--route)"
              strokeWidth={2}
              className="route-trace-line"
            />
          )}
          {rows.map((row, i) => (
            <g key={row.node.commit.id}>
              {row.passThroughLanes.map((lane) => (
                <line
                  key={lane}
                  x1={xFor(lane)}
                  y1={i * ROW_HEIGHT}
                  x2={xFor(lane)}
                  y2={(i + 1) * ROW_HEIGHT}
                  stroke="var(--ink-muted)"
                  strokeWidth={1}
                />
              ))}
              {row.edgesDown.map((edge, ei) =>
                edge.fromLane === 0 && edge.toLane === 0 ? null : (
                  <line
                    key={ei}
                    x1={xFor(edge.fromLane)}
                    y1={yFor(i)}
                    x2={xFor(edge.toLane)}
                    y2={(i + 1) * ROW_HEIGHT}
                    stroke={edge.fromLane === 0 || edge.toLane === 0 ? "var(--route)" : "var(--ink-muted)"}
                    strokeWidth={edge.fromLane === 0 || edge.toLane === 0 ? 2 : 1}
                  />
                )
              )}
              {row.node.isHead ? (
                <>
                  <circle cx={xFor(row.node.lane)} cy={yFor(i)} r={NODE_R + 3} fill="var(--route-tint)" />
                  <circle cx={xFor(row.node.lane)} cy={yFor(i)} r={NODE_R} fill="var(--route)" />
                </>
              ) : row.node.isMerge ? (
                <rect
                  x={xFor(row.node.lane) - NODE_R * 0.8}
                  y={yFor(i) - NODE_R * 0.8}
                  width={NODE_R * 1.6}
                  height={NODE_R * 1.6}
                  transform={`rotate(45 ${xFor(row.node.lane)} ${yFor(i)})`}
                  fill="var(--surface)"
                  stroke={row.node.lane === 0 ? "var(--route)" : "var(--ink-muted)"}
                  strokeWidth={1.5}
                />
              ) : (
                // A miniature 3-stone cairn stack (redesign spec, section 6), not a
                // plain dot - the same stacked-ellipse language as the wordmark
                // mark, scaled down to waypoint size.
                <g fill={row.node.lane === 0 ? "var(--route)" : "var(--ink-muted)"}>
                  <ellipse cx={xFor(row.node.lane)} cy={yFor(i) + 3} rx={NODE_R} ry={2} opacity={0.9} />
                  <ellipse cx={xFor(row.node.lane)} cy={yFor(i)} rx={NODE_R * 0.68} ry={1.7} opacity={0.95} />
                  <ellipse cx={xFor(row.node.lane)} cy={yFor(i) - 2.6} rx={NODE_R * 0.4} ry={1.3} />
                </g>
              )}
            </g>
          ))}
        </svg>
        <ul className="flex-1 min-w-0">
          {rows.map((row) => {
            const { commit } = row.node;
            const firstLine = commit.message.split("\n")[0];
            return (
              <li key={commit.id} style={{ height: ROW_HEIGHT }} className="flex items-center gap-2 min-w-0 text-sm">
                <Link
                  href={`/${owner}/${repo}/commit/${commit.id}`}
                  className="truncate hover:underline hover:text-route min-w-0"
                >
                  {row.node.isHead && <span className="font-mono text-xs text-route mr-1.5">HEAD</span>}
                  {firstLine}
                </Link>
                <span className="font-mono text-xs text-ink-muted shrink-0 ml-auto">{commit.id.slice(0, 7)}</span>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}
