import type { Label } from "@/lib/api";

/** Readable text color for an arbitrary label background: light text on dark colors, dark text on light ones. */
function textColorFor(hexColor: string): string {
  const r = parseInt(hexColor.slice(0, 2), 16) || 0;
  const g = parseInt(hexColor.slice(2, 4), 16) || 0;
  const b = parseInt(hexColor.slice(4, 6), 16) || 0;
  const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return luminance > 0.6 ? "#1f2328" : "#ffffff";
}

/** Redesign spec, section 8: labels are --r-sm, mono, small; author-chosen colors are kept (constrained by the label-creation form, not this component). */
export function LabelChip({ label }: { label: Label }) {
  return (
    <span
      className="inline-flex items-center rounded-sm px-2 py-0.5 font-mono text-xs font-medium shrink-0"
      style={{ backgroundColor: `#${label.color}`, color: textColorFor(label.color) }}
    >
      {label.name}
    </span>
  );
}
