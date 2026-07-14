import { Skeleton } from "@/components/ui/Skeleton";

export default function CommitLoading() {
  return (
    <div>
      <div className="border-b border-hairline px-4 pt-3 bg-surface pb-3">
        <Skeleton className="h-4 w-40 mb-3" />
        <Skeleton className="h-6 w-full max-w-md" />
      </div>
      <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <Skeleton className="h-7 w-7 rounded-full" />
          <Skeleton className="h-4 w-64" />
        </div>
        <Skeleton className="h-72 w-full" />
      </div>
    </div>
  );
}
