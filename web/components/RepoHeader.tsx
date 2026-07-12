import Link from "next/link";

export function RepoHeader({ owner, repo, active }: { owner: string; repo: string; active: string }) {
  const tabs = [
    { key: "code", label: "Code", href: `/${owner}/${repo}` },
    { key: "issues", label: "Issues", href: `/${owner}/${repo}/issues` },
    { key: "pulls", label: "Pull requests", href: `/${owner}/${repo}/pulls` },
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
