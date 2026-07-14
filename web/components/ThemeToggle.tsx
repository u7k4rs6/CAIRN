"use client";

import { useEffect, useState } from "react";

const STORAGE_KEY = "cairn-theme";

function applyTheme(theme: "light" | "night") {
  document.documentElement.setAttribute("data-theme", theme);
  localStorage.setItem(STORAGE_KEY, theme);
}

/**
 * Redesign spec, section 5: two themes, honoring `prefers-color-scheme` by
 * default, with a manual toggle that overrides it. The override is a real
 * `data-theme` attribute the toggle sets and persists (globals.css's selectors
 * key off it); an inline script in the root layout's `<head>` applies any stored
 * choice before first paint, so there is no flash of the wrong theme on load.
 */
export function ThemeToggle() {
  const [resolved, setResolved] = useState<"light" | "night">("light");

  // Syncing from an external system (the DOM attribute the no-flash script or a
  // prior toggle click already set, and matchMedia) that isn't knowable at render
  // time on the server - exactly the case react-hooks/set-state-in-effect's own
  // message calls out as legitimate, not a candidate for a lazy useState
  // initializer (which would read `document` during SSR and mismatch on hydrate).
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    const stored = document.documentElement.getAttribute("data-theme");
    if (stored === "light" || stored === "night") {
      setResolved(stored);
      return;
    }
    setResolved(window.matchMedia("(prefers-color-scheme: dark)").matches ? "night" : "light");
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  function toggle() {
    const next = resolved === "night" ? "light" : "night";
    applyTheme(next);
    setResolved(next);
  }

  return (
    <button
      onClick={toggle}
      aria-label={resolved === "night" ? "Switch to survey sheet (light theme)" : "Switch to night navigation (dark theme)"}
      title={resolved === "night" ? "Survey sheet" : "Night navigation"}
      className="inline-flex items-center justify-center w-8 h-8 rounded border border-hairline text-ink-2 hover:text-ink hover:border-contour transition-colors"
    >
      <span aria-hidden="true">{resolved === "night" ? "\u{263E}" : "\u{2600}"}</span>
    </button>
  );
}
