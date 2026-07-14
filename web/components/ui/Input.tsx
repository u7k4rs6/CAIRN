import type { InputHTMLAttributes, TextareaHTMLAttributes, SelectHTMLAttributes } from "react";

const FIELD_CLASSES =
  "border border-hairline rounded bg-surface px-2.5 py-1.5 text-sm text-ink placeholder:text-ink-muted focus:border-route disabled:opacity-50";

/** Hairline border, --route focus ring (redesign spec, section 8). Mono only when the field itself holds structural data (paths, refs) - set via className. */
export function Input({ className = "", ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${FIELD_CLASSES} ${className}`} />;
}

export function Textarea({ className = "", ...props }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={`${FIELD_CLASSES} ${className}`} />;
}

export function Select({ className = "", ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${FIELD_CLASSES} ${className}`} />;
}
