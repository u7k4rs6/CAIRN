import type { CommitView } from "@/lib/api";

export type GraphNode = {
  commit: CommitView;
  lane: number;
  isMerge: boolean;
  isHead: boolean;
};

export type GraphEdge = {
  fromLane: number;
  toLane: number;
};

export type GraphRow = {
  node: GraphNode;
  /** Lanes with a plain pass-through vertical line at this row (not this row's node). */
  passThroughLanes: number[];
  /** One edge per parent, from this row's node lane to the lane that parent will occupy. */
  edgesDown: GraphEdge[];
};

export type RouteGraph = {
  rows: GraphRow[];
  laneCount: number;
};

/**
 * Derives a minimal lane layout for the route-map commit graph from commit data
 * the app already fetches (redesign spec, section 6: "if it only returns parents,
 * derive lanes minimally in the client from data already fetched. Do not add a
 * backend endpoint."). `commits` is `RevWalk.history`'s own order (newest
 * committer-time first - see CommitView/api.ts), which is what this algorithm
 * assumes: each commit is laid out only after every one of its children (if any
 * were in view) has already claimed or released a lane for it.
 *
 * This is a simplified single-pass version of the classic `git log --graph`
 * layout: one active "lane" per line of development, a lane occupied by a commit
 * is handed off to that commit's first parent (so a lane reads as one continuous
 * branch line), and every additional parent of a merge commit spawns a new lane.
 * It does not attempt Git's own lane-reuse heuristics (packing lanes to minimize
 * width) - lanes are only ever freed at a root commit or a merge convergence, so a
 * long-lived repo with many short-lived branches would end up wider than real
 * `git log --graph`, not narrower. Fine for the teaser/history views this backs;
 * named here as the honest limit of a client-only derivation.
 */
export function buildRouteGraph(commits: CommitView[]): RouteGraph {
  const lanes: Array<string | null> = [];
  const rows: GraphRow[] = [];

  function findFreeLane(): number {
    const idx = lanes.indexOf(null);
    if (idx !== -1) {
      return idx;
    }
    lanes.push(null);
    return lanes.length - 1;
  }

  commits.forEach((commit, index) => {
    const matches: number[] = [];
    lanes.forEach((awaited, i) => {
      if (awaited === commit.id) {
        matches.push(i);
      }
    });

    const homeLane = matches.length > 0 ? Math.min(...matches) : findFreeLane();

    const passThroughLanes: number[] = [];
    lanes.forEach((awaited, i) => {
      if (i !== homeLane && awaited !== null && !matches.includes(i)) {
        passThroughLanes.push(i);
      }
    });

    for (const i of matches) {
      if (i !== homeLane) {
        lanes[i] = null;
      }
    }

    const edgesDown: GraphEdge[] = [];
    if (commit.parents.length === 0) {
      lanes[homeLane] = null;
    } else {
      lanes[homeLane] = commit.parents[0];
      edgesDown.push({ fromLane: homeLane, toLane: homeLane });
      for (let p = 1; p < commit.parents.length; p++) {
        const newLane = findFreeLane();
        lanes[newLane] = commit.parents[p];
        edgesDown.push({ fromLane: homeLane, toLane: newLane });
      }
    }

    rows.push({
      node: { commit, lane: homeLane, isMerge: commit.parents.length > 1, isHead: index === 0 },
      passThroughLanes,
      edgesDown,
    });
  });

  const laneCount = Math.max(1, ...rows.map((r) => r.node.lane + 1), ...lanes.map((_, i) => i + 1));
  return { rows, laneCount };
}
