import { Skeleton } from "@/components/ui/Skeleton";

export default function CommitsLoading() {
  return (
    <div>
      <div className="border-b border-hairline px-4 pt-3 bg-surface pb-3">
        <Skeleton className="h-4 w-40 mb-3" />
        <Skeleton className="h-6 w-full max-w-md" />
      </div>
      <div className="px-4 py-4 max-w-4xl mx-auto flex flex-col gap-2">
        <Skeleton className="h-4 w-56 mb-2" />
        <Skeleton className="h-80 w-full" />
      </div>
    </div>
  );
}
