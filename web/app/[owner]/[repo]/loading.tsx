import { Skeleton } from "@/components/ui/Skeleton";

export default function RepoHomeLoading() {
  return (
    <div>
      <div className="border-b border-hairline px-4 pt-3 bg-surface pb-3">
        <Skeleton className="h-4 w-40 mb-3" />
        <Skeleton className="h-6 w-full max-w-md" />
      </div>
      <div className="px-4 py-4 max-w-5xl mx-auto grid grid-cols-1 lg:grid-cols-[1fr_260px] gap-6">
        <div className="flex flex-col gap-4">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-48 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
        <Skeleton className="h-32 w-full" />
      </div>
    </div>
  );
}
