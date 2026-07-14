import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "destructive";

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: "bg-route text-on-route border border-route hover:bg-route-ink hover:border-route-ink",
  secondary: "bg-transparent text-ink border border-hairline hover:border-contour hover:text-route",
  destructive: "bg-transparent text-survey-red border border-survey-red hover:bg-survey-red hover:text-on-route",
};

/** Rectangular, --r, matte (redesign spec, section 8). One shared shape for every button in the app. */
export function Button({
  variant = "secondary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...props}
      className={`rounded px-3 py-1.5 text-sm font-medium transition-colors disabled:opacity-50 disabled:pointer-events-none ${VARIANT_CLASSES[variant]} ${className}`}
    />
  );
}
