const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    cache: "no-store",
    headers: { "Content-Type": "application/json", ...(init?.headers || {}) },
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
export type Issue = { id: number; title: string; body: string; state: "OPEN" | "CLOSED"; author: { username: string } };
export type PullRequestView = {
  id: number;
  title: string;
  sourceRef: string;
  targetRef: string;
  state: string;
  author: { username: string };
};
export type SearchLineMatch = { lineNumber: number; line: string };
export type SearchFileMatch = { path: string; lines: SearchLineMatch[] };
export type SearchResponse = { indexing: boolean; queryTooShort: boolean; results: SearchFileMatch[] };

export const api = {
  tree: (owner: string, repo: string, ref: string, path: string[] = []) =>
    request<TreeEntry[]>(`/api/repos/${owner}/${repo}/tree/${ref}/${path.join("/")}`),
  blob: (owner: string, repo: string, ref: string, path: string[]) =>
    request<BlobView>(`/api/repos/${owner}/${repo}/blob/${ref}/${path.join("/")}`),
  commits: (owner: string, repo: string, ref: string) =>
    request<CommitView[]>(`/api/repos/${owner}/${repo}/commits/${ref}`),
  commitDiff: (owner: string, repo: string, sha: string) =>
    request<CommitDiff>(`/api/repos/${owner}/${repo}/commit/${sha}`),
  issues: (owner: string, repo: string) => request<Issue[]>(`/api/repos/${owner}/${repo}/issues`),
  issue: async (owner: string, repo: string, n: string) => {
    const all = await request<Issue[]>(`/api/repos/${owner}/${repo}/issues`);
    return all.find((i) => String(i.id) === n) ?? null;
  },
  pulls: (owner: string, repo: string) => request<PullRequestView[]>(`/api/repos/${owner}/${repo}/pulls`),
  pull: async (owner: string, repo: string, n: string) => {
    const all = await request<PullRequestView[]>(`/api/repos/${owner}/${repo}/pulls`);
    return all.find((p) => String(p.id) === n) ?? null;
  },
  search: (owner: string, repo: string, q: string) =>
    request<SearchResponse>(`/api/repos/${owner}/${repo}/search?q=${encodeURIComponent(q)}`),
};
