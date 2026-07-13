import type { Metadata } from "next";
import Link from "next/link";
import { SessionStatus } from "@/components/SessionStatus";
import "./globals.css";

export const metadata: Metadata = {
  title: "Cairn",
  description: "Self-hosted Git hosting and collaboration",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full">
      <body className="min-h-full flex flex-col">
        <header className="border-b border-border px-4 py-2 flex items-center gap-4">
          <Link href="/" className="font-semibold tracking-tight">
            Cairn
          </Link>
          <span className="text-fg-muted text-sm">self-hosted Git</span>
          <SessionStatus />
        </header>
        <main className="flex-1">{children}</main>
      </body>
    </html>
  );
}
