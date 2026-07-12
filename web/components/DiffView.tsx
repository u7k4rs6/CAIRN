import type { FileDiff } from "@/lib/api";

type Row = { kind: "equal" | "add" | "del"; oldNo: number | null; newNo: number | null; text: string };

function rowsFor(diff: FileDiff): Row[] {
  const rows: Row[] = [];
  for (const edit of diff.edits) {
    if (edit.type === "EQUAL") {
      for (let i = 0; i < edit.origEnd - edit.origStart; i++) {
        rows.push({
          kind: "equal",
          oldNo: edit.origStart + i + 1,
          newNo: edit.revStart + i + 1,
          text: diff.newLines[edit.revStart + i] ?? "",
        });
      }
    } else if (edit.type === "DELETE") {
      for (let i = edit.origStart; i < edit.origEnd; i++) {
        rows.push({ kind: "del", oldNo: i + 1, newNo: null, text: diff.oldLines[i] ?? "" });
      }
    } else {
      for (let i = edit.revStart; i < edit.revEnd; i++) {
        rows.push({ kind: "add", oldNo: null, newNo: i + 1, text: diff.newLines[i] ?? "" });
      }
    }
  }
  return rows;
}

/**
 * The diff is the product (frontend spec, section 1). Rendering note: this renders
 * every row directly rather than virtualizing (section 5.3's ideal is O(visible
 * rows), not O(file length)); see DECISIONS.md for the scope tradeoff. Fine at the
 * size a demo repo's diffs reach, not the right choice for a very large real diff.
 */
export function DiffView({ diffs }: { diffs: FileDiff[] }) {
  if (diffs.length === 0) {
    return <p className="text-fg-muted text-sm px-4 py-8">No changes.</p>;
  }
  return (
    <div className="flex flex-col gap-4">
      {diffs.map((diff) => (
        <div key={diff.path} className="border border-border rounded overflow-hidden">
          <div className="bg-bg-subtle px-3 py-1.5 text-sm font-mono-data flex items-center gap-2">
            <span className={
              diff.kind === "ADDED" ? "text-success" : diff.kind === "DELETED" ? "text-danger" : "text-fg-muted"
            }>
              {diff.kind === "ADDED" ? "added" : diff.kind === "DELETED" ? "deleted" : "modified"}
            </span>
            <span>{diff.path}</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs font-mono-data border-collapse">
              <tbody>
                {rowsFor(diff).map((row, i) => (
                  <tr
                    key={i}
                    className={
                      row.kind === "add"
                        ? "bg-success/10"
                        : row.kind === "del"
                        ? "bg-danger/10"
                        : ""
                    }
                  >
                    <td className="select-none text-right pr-2 text-fg-muted w-10 border-r border-border">
                      {row.oldNo ?? ""}
                    </td>
                    <td className="select-none text-right pr-2 text-fg-muted w-10 border-r border-border">
                      {row.newNo ?? ""}
                    </td>
                    <td className="pl-2 whitespace-pre">
                      {row.kind === "add" ? "+" : row.kind === "del" ? "-" : " "}
                      {row.text}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ))}
    </div>
  );
}
