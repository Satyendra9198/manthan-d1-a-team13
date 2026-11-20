import { toast } from "sonner";
import { isApiError } from "../api/client";

/** Error toast: backend `message` + "Ref: <correlation_id>" (contract §Conventions). */
export function toastError(err: unknown, fallback = "Something went wrong. Please try again."): void {
  if (isApiError(err)) {
    toast.error(err.message || fallback, {
      description: err.correlationId ? `Ref: ${err.correlationId}` : undefined,
      id: err.correlationId ?? undefined,
    });
    return;
  }
  if (err instanceof DOMException && err.name === "AbortError") return;
  toast.error(fallback);
}

export const STALE_MESSAGE = "This return was updated by someone else — reloaded";
export const IN_PROGRESS_MESSAGE = "Still processing your earlier request — hang tight.";
