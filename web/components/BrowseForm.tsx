"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { parseOwnerRepoPath } from "@/lib/repoPath";
import { Input } from "@/components/ui/Input";
import { Button } from "@/components/ui/Button";

/**
 * The homepage's "enter a repository's path" box. Client-side navigation only
 * (router.push with a relative path) - never an absolute URL, so this never
 * depends on what host/port the server itself is bound to.
 */
export function BrowseForm() {
  const [value, setValue] = useState("");
  const router = useRouter();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const path = parseOwnerRepoPath(value);
    if (path) {
      router.push(`/${path}`);
    }
  }

  return (
    <form onSubmit={submit} className="flex gap-2 w-full max-w-md">
      <Input
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="owner/repo"
        className="flex-1 font-mono"
      />
      <Button type="submit" variant="primary">
        Browse
      </Button>
    </form>
  );
}
