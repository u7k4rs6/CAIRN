import { AuthBar } from "@/components/AuthBar";
import { CreateOrgForm } from "@/components/CreateOrgForm";

export default function NewOrgPage() {
  return (
    <div className="px-4 py-4 max-w-2xl mx-auto flex flex-col gap-4">
      <h1 className="text-lg font-semibold">New organization</h1>
      <AuthBar />
      <CreateOrgForm />
    </div>
  );
}
