import Link from "next/link";
import { api } from "@/lib/api";
import { LogoutButton } from "@/components/LogoutButton";

/**
 * Server-rendered: the layout can see the session cookie directly (lib/api.ts
 * forwards it), no client fetch needed just to show who is signed in.
 *
 * <p>This renders on every single page via the root layout, so any failure here
 * (the API being briefly unreachable, a network hiccup) must degrade to "not
 * signed in" rather than take the whole page down with it; {@code api.me()} only
 * narrows a 401 to null itself, so a broader catch covers everything else.
 */
export async function SessionStatus() {
  const me = await api.me().catch(() => null);
  if (!me) {
    return (
      <Link href="/login" className="text-sm text-accent hover:underline ml-auto">
        Sign in
      </Link>
    );
  }
  return (
    <span className="ml-auto flex items-center gap-2 text-sm text-fg-muted">
      Signed in as <span className="font-medium text-fg">{me.username}</span>
      <LogoutButton />
    </span>
  );
}
