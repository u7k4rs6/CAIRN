import { redirect } from "next/navigation";

export default async function RepoSettingsPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string }>;
}) {
  const { owner, repo } = await params;
  redirect(`/${owner}/${repo}/settings/access`);
}
