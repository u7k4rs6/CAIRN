import { RepoHeader } from "@/components/RepoHeader";
import { AuthBar } from "@/components/AuthBar";
import { AccessSettingsPanel } from "@/components/AccessSettingsPanel";

export default async function RepoAccessSettingsPage({
  params,
}: {
  params: Promise<{ owner: string; repo: string }>;
}) {
  const { owner, repo } = await params;
  return (
    <div>
      <RepoHeader owner={owner} repo={repo} active="settings" />
      <div className="px-4 pt-4">
        <AuthBar />
      </div>
      <AccessSettingsPanel owner={owner} repo={repo} />
    </div>
  );
}
