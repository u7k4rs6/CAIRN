import { AuthBar } from "@/components/AuthBar";
import { OrgTeamsPanel } from "@/components/OrgTeamsPanel";

export default async function OrgTeamsPage({ params }: { params: Promise<{ org: string }> }) {
  const { org } = await params;
  return (
    <div className="px-4 py-4 max-w-2xl mx-auto flex flex-col gap-4">
      <AuthBar />
      <OrgTeamsPanel org={org} />
    </div>
  );
}
