import Link from "next/link";
import { CairnMark } from "@/components/CairnMark";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

export default function HomePage() {
  return (
    <div className="contour-bg max-w-2xl mx-auto px-4 py-20 flex flex-col items-center text-center">
      <CairnMark size={40} className="text-route mb-3" />
      <h1 className="text-display font-display text-ink mb-2">cairn</h1>
      <p className="text-ink-2 mb-8 max-w-md">
        A self-hosted Git host built on a real content-addressable version-control engine.
        Enter a repository&rsquo;s path to start browsing.
      </p>
      <form action="/goto" className="flex gap-2 w-full max-w-md">
        <Input name="path" placeholder="owner/repo" className="flex-1 font-mono" />
        <Button type="submit" variant="primary">
          Browse
        </Button>
      </form>
      <p className="text-ink-muted text-xs mt-4">
        Example:{" "}
        <Link href="/acme/demo" className="text-route hover:underline font-mono">
          acme/demo
        </Link>
      </p>
      <p className="text-ink-muted text-xs mt-2 flex flex-wrap items-center justify-center gap-x-1">
        <Link href="/login" className="text-route hover:underline">Sign in</Link>
        <span>&middot;</span>
        <Link href="/signup" className="text-route hover:underline">Create an account</Link>
        <span>&middot;</span>
        <Link href="/repos/new" className="text-route hover:underline">Create a repository</Link>
        <span>&middot;</span>
        <Link href="/orgs/new" className="text-route hover:underline">Create an organization</Link>
      </p>
    </div>
  );
}
