import { AlertTriangle, RefreshCw, SearchX } from "lucide-react";
import { isApiError } from "../api/client";
import { Button } from "./ui/Button";
import { EmptyState } from "./ui/EmptyState";

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const notFound = isApiError(error) && error.status === 404;
  const message = isApiError(error) ? error.message : "Something went wrong while loading this page.";
  const ref = isApiError(error) ? error.correlationId : null;
  return (
    <EmptyState
      icon={notFound ? <SearchX /> : <AlertTriangle />}
      title={notFound ? "Not found" : "Couldn't load this"}
      description={
        <>
          {message}
          {ref ? <span className="mt-1 block font-mono text-xs text-fg-subtle">Ref: {ref}</span> : null}
        </>
      }
      action={
        onRetry && !notFound ? (
          <Button variant="outline" onClick={onRetry} icon={<RefreshCw className="size-4" />}>
            Try again
          </Button>
        ) : undefined
      }
      testId="error-state"
    />
  );
}
