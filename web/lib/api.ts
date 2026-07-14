import { cookies } from "next/headers";
import { apiBase } from "./apiBase";

const API_BASE = apiBase();

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

/**
 * Forwards the browser's own {@code cairn_session} cookie to the API on every
 * Server Component fetch. Without this, a Server Component (which runs on the
 * Next.js server, not in the browser) has no way to see the session a real
 * logged-in visitor holds, so every read on this app's read-first pages would be
 * anonymous no matter who was looking at the page - the actual, previously-
 * unreported gap the P2 session-auth work exists to close (see LoginController's
 * Javadoc for the other half): a private repo's own owner could never see it
 * through any of these pages, only through a client-island fetch carrying a
 * manually-pasted PAT. Reading cookies() is only valid in a Server Component, which
 * is the only context every caller of {@code api.*} runs in (client islands do
 * their own fetches directly; see AccessSettingsPanel's doc comment for why).
 */
async function forwardedCookieHeader(): Promise<string> {
  const store = await cookies();
  return store.toString();
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const cookieHeader = await forwardedCookieHeader();
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...(cookieHeader ? { Cookie: cookieHeader } : {}),
      ...(init?.headers || {}),
    },
  });
  if (!res.ok) {
    throw new ApiError(res.status, `${init?.method || "GET"} ${path} failed: ${res.status}`);
  }
  const text = await res.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export type TreeEntry = { name: string; mode: string; kind: "tree" | "blob"; id: string };
export type CommitView = {
  id: string;
  message: string;
  authorName: string;
  authorEmail: string;
  authorTime: number;
  parents: string[];
};
export type Edit = { type: "EQUAL" | "INSERT" | "DELETE"; origStart: number; origEnd: number; revStart: number; revEnd: number };
export type FileDiff = {
  path: string;
  kind: "ADDED" | "DELETED" | "MODIFIED";
  oldLines: string[];
  newLines: string[];
  edits: Edit[];
};
export type CommitDiff = { commit: CommitView; diffs: FileDiff[] };
export type BlobView = { path: string; content: string; size: number };
export type Label = { id: number; name: string; color: string };
export type Milestone = { id: number; title: string; description: string | null; dueAt: string | null; state: "OPEN" | "CLOSED" };
export type Issue = {
  id: number;
  title: string;
  body: string;
  state: "OPEN" | "CLOSED";
  author: { username: string };
  labels: Label[];
  assignees: { username: string }[];
  milestone: Milestone | null;
};
export type IssueFilters = { state?: string; label?: string; assignee?: string; milestone?: string };
export type PullRequestView = {
  id: number;
  title: string;
  sourceRef: string;
  targetRef: string;
  state: string;
  author: { username: string };
};
export type CompareResult = { commits: CommitView[]; diffs: FileDiff[] };
export type SearchLineMatch = { lineNumber: number; line: string };
export type SearchFileMatch = { path: string; lines: SearchLineMatch[] };
export type SearchResponse = { indexing: boolean; queryTooShort: boolean; results: SearchFileMatch[] };
export type Me = { username: string; email: string };
export type ConversationEntry = {
  kind: "REVIEW" | "COMMENT";
  id: number;
  author: string;
  verdict: "APPROVE" | "REQUEST_CHANGES" | "COMMENT" | null;
  body: string;
  path: string | null;
  line: number | null;
};
export type RepoSummary = { id: number; name: string; owner: string; visibility: "PUBLIC" | "INTERNAL" | "PRIVATE" };
export type Branch = { name: string; tip: string };
export type RepoStats = { branchCount: number; commitCount: number; languages: Record<string, number> };

export const api = {
  tree: (owner: string, repo: string, ref: string, path: string[] = []) =>
    request<TreeEntry[]>(`/api/repos/${owner}/${repo}/tree/${ref}/${path.join("/")}`),
  blob: (owner: string, repo: string, ref: string, path: string[]) =>
    request<BlobView>(`/api/repos/${owner}/${repo}/blob/${ref}/${path.join("/")}`),
  commits: (owner: string, repo: string, ref: string) =>
    request<CommitView[]>(`/api/repos/${owner}/${repo}/commits/${ref}`),
  commitDiff: (owner: string, repo: string, sha: string) =>
    request<CommitDiff>(`/api/repos/${owner}/${repo}/commit/${sha}`),
  issues: (owner: string, repo: string, filters: IssueFilters = {}) => {
    const params = new URLSearchParams(Object.entries(filters).filter(([, v]) => v) as [string, string][]);
    const query = params.toString();
    return request<Issue[]>(`/api/repos/${owner}/${repo}/issues${query ? `?${query}` : ""}`);
  },
  issue: async (owner: string, repo: string, n: string) => {
    const all = await request<Issue[]>(`/api/repos/${owner}/${repo}/issues`);
    return all.find((i) => String(i.id) === n) ?? null;
  },
  labels: (owner: string, repo: string) => request<Label[]>(`/api/repos/${owner}/${repo}/labels`),
  milestones: (owner: string, repo: string) => request<Milestone[]>(`/api/repos/${owner}/${repo}/milestones`),
  pulls: (owner: string, repo: string) => request<PullRequestView[]>(`/api/repos/${owner}/${repo}/pulls`),
  pull: async (owner: string, repo: string, n: string) => {
    try {
      return await request<PullRequestView>(`/api/repos/${owner}/${repo}/pulls/${n}`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        return null;
      }
      throw e;
    }
  },
  compare: (owner: string, repo: string, base: string, head: string) =>
    request<CompareResult>(`/api/repos/${owner}/${repo}/compare/${base}...${head}`),
  search: (owner: string, repo: string, q: string) =>
    request<SearchResponse>(`/api/repos/${owner}/${repo}/search?q=${encodeURIComponent(q)}`),
  conversation: (owner: string, repo: string, n: number) =>
    request<ConversationEntry[]>(`/api/repos/${owner}/${repo}/pulls/${n}/conversation`),
  ownerRepos: (owner: string) => request<RepoSummary[]>(`/api/repos/${owner}`),
  branches: (owner: string, repo: string) => request<Branch[]>(`/api/repos/${owner}/${repo}/branches`),
  stats: (owner: string, repo: string) => request<RepoStats>(`/api/repos/${owner}/${repo}/stats`),
  me: async () => {
    try {
      return await request<Me>(`/api/me`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        return null;
      }
      throw e;
    }
  },
};
