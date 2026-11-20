import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, qs } from "../client";
import type { ApiResult } from "../client";
import type { ListResponse, RefundMethod, ReturnDTO, ReturnStatus } from "../types";
import { handleReturnMutationError, pollWhileRefundPending } from "./common";

export const financeKeys = {
  all: ["finance"] as const,
  queue: (status: string) => ["finance", "queue", status] as const,
  ret: (id: string) => ["finance", "returns", id] as const,
};

export function useFinanceQueue(statuses: ReturnStatus[]) {
  const status = statuses.join(",");
  return useQuery({
    queryKey: financeKeys.queue(status),
    queryFn: ({ signal }) => api.get<ListResponse<ReturnDTO>>(`/finance/queue${qs({ status })}`, signal),
    refetchInterval: 20_000,
  });
}

export function useFinanceReturn(id: string) {
  return useQuery({
    queryKey: financeKeys.ret(id),
    queryFn: ({ signal }) => api.get<ReturnDTO>(`/finance/returns/${encodeURIComponent(id)}`, signal),
    refetchInterval: pollWhileRefundPending,
  });
}

type Outcome = { ret: ReturnDTO; accepted: boolean };

function toOutcome(r: ApiResult<ReturnDTO>): Outcome {
  // 202 = refund outcome UNKNOWN/PENDING; the UI polls until it leaves REFUND_PENDING.
  return { ret: r.data, accepted: r.status === 202 };
}

function useFinanceMutation<V extends { ret: ReturnDTO; idempotencyKey: string }>(
  path: (ret: ReturnDTO) => string,
  body: (vars: V) => unknown,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: V) =>
      toOutcome(
        await api.post<ReturnDTO>(path(vars.ret), {
          body: body(vars),
          idempotencyKey: vars.idempotencyKey,
          ifMatch: vars.ret.version,
        }),
      ),
    onSuccess: ({ ret }) => {
      qc.setQueryData(financeKeys.ret(ret.id), ret);
      void qc.invalidateQueries({ queryKey: ["finance", "queue"] });
    },
    onError: (err, vars) => handleReturnMutationError(err, qc, [financeKeys.ret(vars.ret.id)]),
  });
}

export const useApproveRefund = () =>
  useFinanceMutation<{ ret: ReturnDTO; idempotencyKey: string; note: string }>(
    (r) => `/finance/returns/${encodeURIComponent(r.id)}/approve`,
    (v) => (v.note.trim() ? { note: v.note.trim() } : {}),
  );

export const useRejectRefund = () =>
  useFinanceMutation<{ ret: ReturnDTO; idempotencyKey: string; note: string }>(
    (r) => `/finance/returns/${encodeURIComponent(r.id)}/reject`,
    (v) => ({ note: v.note.trim() }),
  );

export const useRetryRefund = () =>
  useFinanceMutation<{ ret: ReturnDTO; idempotencyKey: string; method: RefundMethod | null }>(
    (r) => `/finance/returns/${encodeURIComponent(r.id)}/refund/retry`,
    (v) => (v.method ? { method: v.method } : {}),
  );

/** Recheck runs the reconciler for one UNKNOWN attempt; it never creates a new attempt. */
export function useRecheckAttempt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { attemptId: string; returnId: string }) =>
      (await api.post<ReturnDTO>(`/finance/refund-attempts/${encodeURIComponent(vars.attemptId)}/recheck`)).data,
    onSuccess: (ret) => {
      qc.setQueryData(financeKeys.ret(ret.id), ret);
      void qc.invalidateQueries({ queryKey: ["finance", "queue"] });
    },
    onError: (err, vars) => handleReturnMutationError(err, qc, [financeKeys.ret(vars.returnId)]),
  });
}
