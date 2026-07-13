import Link from "next/link";

export default function HomePage() {
  return (
    <div className="max-w-2xl mx-auto px-4 py-16">
      <h1 className="text-2xl font-semibold mb-2">Cairn</h1>
      <p className="text-fg-muted mb-8">
        A self-hosted Git hosting and collaboration platform. Browse a repository to get started.
      </p>
      <form action="/goto" className="flex gap-2">
        <input
          name="path"
          placeholder="owner/repo"
          className="flex-1 border border-border rounded px-3 py-2 bg-bg-subtle font-mono-data text-sm"
        />
        <button type="submit" className="bg-accent text-accent-fg rounded px-4 py-2 text-sm font-medium">
          Browse
        </button>
      </form>
      <p className="text-fg-muted text-xs mt-4">
        Example: <Link href="/acme/demo" className="text-accent underline">acme/demo</Link>
      </p>
      <p className="text-fg-muted text-xs mt-2">
        <Link href="/login" className="text-accent underline">Sign in</Link>
        {" · "}
        <Link href="/signup" className="text-accent underline">Create an account</Link>
        {" · "}
        <Link href="/repos/new" className="text-accent underline">Create a repository</Link>
        {" · "}
        <Link href="/orgs/new" className="text-accent underline">Create an organization</Link>
      </p>
    </div>
  );
}
