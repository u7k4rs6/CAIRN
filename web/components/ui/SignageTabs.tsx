import Link from "next/link";

export type TabItem = { key: string; label: string; href: string };

/** Signage-style tabs, mono labels, --route active underline (redesign spec, section 8). */
export function SignageTabs({ tabs, active }: { tabs: TabItem[]; active: string }) {
  return (
    <nav className="flex gap-5 text-sm border-b border-hairline">
      {tabs.map((tab) => (
        <Link
          key={tab.key}
          href={tab.href}
          aria-current={active === tab.key ? "page" : undefined}
          className={`font-mono pb-2 border-b-2 -mb-px transition-colors ${
            active === tab.key
              ? "border-route text-ink font-medium"
              : "border-transparent text-ink-muted hover:text-ink"
          }`}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  );
}
