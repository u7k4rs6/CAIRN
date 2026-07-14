"use client";

import { useState } from "react";

/** A copy control for mono data (clone URLs, hashes). Announces success briefly via text, not just an icon swap, for screen readers. */
export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <button
      onClick={copy}
      aria-label={`${label}: ${value}`}
      title={label}
      className="inline-flex items-center justify-center w-6 h-6 rounded text-ink-muted hover:text-route hover:bg-surface-sunken shrink-0"
    >
      <span aria-hidden="true">{copied ? "✓" : "⧉"}</span>
    </button>
  );
}
