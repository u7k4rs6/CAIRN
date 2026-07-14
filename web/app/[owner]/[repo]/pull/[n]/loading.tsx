import { Skeleton } from "@/components/ui/Skeleton";

export default function PullRequestLoading() {
  return (
    <div>
      <div className="border-b border-hairline px-4 pt-3 bg-surface pb-3">
        <Skeleton className="h-4 w-40 mb-3" />
        <Skeleton className="h-6 w-full max-w-md" />
      </div>
      <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-4">
        <Skeleton className="h-7 w-2/3" />
        <Skeleton className="h-5 w-1/3" />
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    </div>
  );
}
