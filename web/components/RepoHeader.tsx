import Link from "next/link";

export function RepoHeader({ owner, repo, active }: { owner: string; repo: string; active: string }) {
  const tabs = [
    { key: "code", label: "Code", href: `/${owner}/${repo}` },
    { key: "issues", label: "Issues", href: `/${owner}/${repo}/issues` },
    { key: "pulls", label: "Pull requests", href: `/${owner}/${repo}/pulls` },
    // Not gated on the current user's role in the tab bar itself: this app has no
    // server-side session, so a Server Component rendering this header cannot know
    // the browser's effective role (see AccessSettingsPanel's doc comment). The
    // destination page itself applies the real gate, masked the same way every
    // other admin/read check in this app already is (security doc, section 6.3).
    { key: "settings", label: "Settings", href: `/${owner}/${repo}/settings/access` },
  ];
  return (
    <div className="border-b border-border px-4 pt-3">
      <div className="text-sm text-fg-muted mb-2">
        <Link href={`/${owner}`} className="hover:underline">{owner}</Link>
        {" / "}
        <Link href={`/${owner}/${repo}`} className="font-semibold text-fg hover:underline">{repo}</Link>
      </div>
      <nav className="flex gap-4 text-sm">
        {tabs.map((tab) => (
          <Link
            key={tab.key}
            href={tab.href}
            className={`pb-2 border-b-2 ${
              active === tab.key ? "border-accent font-medium" : "border-transparent text-fg-muted"
            }`}
          >
            {tab.label}
          </Link>
        ))}
      </nav>
    </div>
  );
}
