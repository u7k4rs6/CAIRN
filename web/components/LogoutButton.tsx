"use client";

import { useRouter } from "next/navigation";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";

export function LogoutButton() {
  const router = useRouter();

  function csrfTokenFromCookie(): string | null {
    return document.cookie.split("; ").find((c) => c.startsWith("cairn_csrf="))?.split("=")[1] ?? null;
  }

  async function logout() {
    await fetch(`${API_BASE}/api/logout`, {
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-Token": csrfTokenFromCookie() || "" },
    });
    router.push("/");
    router.refresh();
  }

  return (
    <button onClick={logout} className="text-accent hover:underline">
      Sign out
    </button>
  );
}
