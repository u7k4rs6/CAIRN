import type { Metadata } from "next";
import Link from "next/link";
import { Overpass, Overpass_Mono } from "next/font/google";
import { SessionStatus } from "@/components/SessionStatus";
import { ThemeToggle } from "@/components/ThemeToggle";
import { CairnMark } from "@/components/CairnMark";
import { GotoSearchBox } from "@/components/GotoSearchBox";
import "./globals.css";

// Self-hosted via next/font (redesign spec, section 4): downloaded at build time
// and served from this origin, not fetched from Google at runtime. next/font also
// computes a size-matched fallback so swapping in the real face causes no layout
// shift, on top of the explicit `display: "swap"` below.
const overpass = Overpass({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-overpass",
  display: "swap",
});
const overpassMono = Overpass_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-overpass-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: "Cairn",
  description: "Self-hosted Git hosting and collaboration",
};

// Applies a stored theme override before first paint, so a returning visitor with
// an explicit choice never sees a flash of the other theme (ThemeToggle writes
// this same key/attribute; globals.css's [data-theme] selectors read it).
const NO_FLASH_THEME_SCRIPT = `
(function () {
  try {
    var stored = localStorage.getItem("cairn-theme");
    if (stored === "light" || stored === "night") {
      document.documentElement.setAttribute("data-theme", stored);
    }
  } catch (e) {}
})();
`;

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`h-full ${overpass.variable} ${overpassMono.variable}`}>
      <head>
        <script dangerouslySetInnerHTML={{ __html: NO_FLASH_THEME_SCRIPT }} />
      </head>
      <body className="min-h-full flex flex-col">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:top-2 focus:left-2 focus:bg-route focus:text-on-route focus:px-3 focus:py-1.5 focus:rounded"
        >
          Skip to content
        </a>
        <header className="border-b border-hairline px-4 py-2 flex items-center gap-4 bg-surface">
          <Link href="/" className="flex items-center gap-2 shrink-0 text-ink hover:text-route transition-colors">
            <CairnMark size={22} />
            <span className="font-display font-bold text-lg tracking-tight">cairn</span>
          </Link>
          <GotoSearchBox />
          <div className="ml-auto flex items-center gap-3">
            <ThemeToggle />
            <SessionStatus />
          </div>
        </header>
        <main id="main" className="flex-1">
          {children}
        </main>
      </body>
    </html>
  );
}
