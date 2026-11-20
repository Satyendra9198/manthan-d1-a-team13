import type { Query, QueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { IN_PROGRESS_MESSAGE, STALE_MESSAGE, toastError } from "../../lib/notify";
import { hasCode, isApiError } from "../client";
import type { ReturnDTO } from "../types";

/** Poll every 3 s while the refund outcome is not final (202 / attempt UNKNOWN, ADR-001). */
export function pollWhileRefundPending(query: Query<ReturnDTO, Error, ReturnDTO, readonly unknown[]>): number | false {
  return query.state.data?.status === "REFUND_PENDING" ? 3000 : false;
}

/**
 * Shared error UX for mutations on a return:
 * - STALE_VERSION → "updated by someone else — reloaded" + refetch
 * - REQUEST_IN_PROGRESS → "still processing" (same Idempotency-Key is still running)
 * - ILLEGAL_TRANSITION → message + refetch (state moved underneath us)
 */
export function handleReturnMutationError(err: unknown, qc: QueryClient, invalidate: readonly (readonly unknown[])[]): void {
  if (hasCode(err, "STALE_VERSION")) {
    toast.warning(STALE_MESSAGE, { description: err.correlationId ? `Ref: ${err.correlationId}` : undefined });
    for (const key of invalidate) void qc.invalidateQueries({ queryKey: key });
    return;
  }
  if (hasCode(err, "REQUEST_IN_PROGRESS")) {
    toast.info(IN_PROGRESS_MESSAGE, { description: err.correlationId ? `Ref: ${err.correlationId}` : undefined });
    return;
  }
  if (hasCode(err, "ILLEGAL_TRANSITION", "RETURN_CANCELLED", "GUARD_FAILED")) {
    for (const key of invalidate) void qc.invalidateQueries({ queryKey: key });
  }
  toastError(err);
}

/**
 * Should a confirm dialog close after this error? Keep it open (same Idempotency-Key) for anything
 * that may be retried safely: network errors, timeouts, 5xx, 429 and REQUEST_IN_PROGRESS.
 */
export function shouldCloseDialogOnError(err: unknown): boolean {
  if (!isApiError(err)) return false;
  if (err.code === "REQUEST_IN_PROGRESS" || err.code === "RATE_LIMITED") return false;
  return err.status >= 400 && err.status < 500;
}
