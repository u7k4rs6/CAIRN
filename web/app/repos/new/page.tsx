import { AuthBar } from "@/components/AuthBar";
import { CreateRepoForm } from "@/components/CreateRepoForm";

export default function NewRepoPage() {
  return (
    <div className="px-4 py-16 max-w-sm mx-auto flex flex-col gap-4">
      <h1 className="text-xl font-semibold">New repository</h1>
      <AuthBar />
      <CreateRepoForm />
    </div>
  );
}
