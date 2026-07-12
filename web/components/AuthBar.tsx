"use client";

import { useEffect, useState } from "react";

/**
 * A stand-in for real login (M8 is a thin UI over the engine; session-based web
 * login is out of scope here, see DECISIONS.md). Stores a username and personal
 * access token in localStorage and exposes them via {@link useAuth} so action
 * components (MergeBox, ReviewComposer, CommentComposer) can attach Basic auth.
 */
export function useAuth() {
  const [username, setUsername] = useState("");
  const [token, setToken] = useState("");

  useEffect(() => {
    setUsername(localStorage.getItem("cairn-username") || "");
    setToken(localStorage.getItem("cairn-token") || "");
  }, []);

  const authHeader: Record<string, string> =
    username && token ? { Authorization: "Basic " + btoa(`${username}:${token}`) } : {};
  return { username, token, authHeader, isAuthenticated: Boolean(username && token) };
}

export function AuthBar() {
  const [username, setUsername] = useState("");
  const [token, setToken] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    setUsername(localStorage.getItem("cairn-username") || "");
    setToken(localStorage.getItem("cairn-token") || "");
  }, []);

  return (
    <div className="border border-border rounded p-3 flex flex-wrap items-end gap-2 text-sm bg-bg-subtle">
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Username</span>
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="border border-border rounded px-2 py-1 bg-bg text-sm w-40"
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-xs text-fg-muted">Personal access token</span>
        <input
          type="password"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          className="border border-border rounded px-2 py-1 bg-bg text-sm w-56"
        />
      </label>
      <button
        onClick={() => {
          localStorage.setItem("cairn-username", username);
          localStorage.setItem("cairn-token", token);
          setSaved(true);
          setTimeout(() => setSaved(false), 1500);
        }}
        className="bg-accent text-accent-fg rounded px-3 py-1.5 text-sm font-medium"
      >
        {saved ? "Saved" : "Save"}
      </button>
    </div>
  );
}
