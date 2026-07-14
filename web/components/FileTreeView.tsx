import Link from "next/link";
import type { TreeEntry } from "@/lib/api";

// Note: the prop is named "gitRef", not "ref" - React reserves the literal prop
// name "ref" for ref-forwarding even on plain data, so a component can't use it as
// an ordinary string prop (it silently never reaches the component as a prop at all).
export function FileTreeView({
  owner,
  repo,
  gitRef,
  path,
  entries,
}: {
  owner: string;
  repo: string;
  gitRef: string;
  path: string[];
  entries: TreeEntry[];
}) {
  if (entries.length === 0) {
    return <p className="text-ink-muted text-sm px-4 py-8">This directory is empty.</p>;
  }
  const sorted = [...entries].sort((a, b) => {
    if (a.kind !== b.kind) return a.kind === "tree" ? -1 : 1;
    return a.name.localeCompare(b.name);
  });
  return (
    <table className="w-full text-sm">
      <tbody>
        {sorted.map((entry) => {
          const entryPath = [...path, entry.name];
          const href =
            entry.kind === "tree"
              ? `/${owner}/${repo}/tree/${gitRef}/${entryPath.join("/")}`
              : `/${owner}/${repo}/blob/${gitRef}/${entryPath.join("/")}`;
          return (
            <tr key={entry.name} className="border-b border-hairline last:border-0 hover:bg-surface-sunken">
              <td className="py-1.5 px-3">
                <Link href={href} className="flex items-center gap-2">
                  <span className="text-ink-muted" aria-hidden="true">
                    {entry.kind === "tree" ? "\u{1F4C1}" : "\u{1F4C4}"}
                  </span>
                  <span className={entry.kind === "tree" ? "font-medium text-ink" : "font-mono text-ink-2"}>{entry.name}</span>
                </Link>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
