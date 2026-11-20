import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, qs } from "../client";
import type {
  AnalyticsOverview,
  AuditEntry,
  ClearFlagResponse,
  FlaggedCustomer,
  ListResponse,
  ReconciliationReport,
} from "../types";

export const managerKeys = {
  overview: (days: number) => ["manager", "overview", days] as const,
  flagged: ["manager", "flagged"] as const,
  audit: (entityId: string, limit: number) => ["manager", "audit", entityId, limit] as const,
  recon: ["manager", "reconciliation"] as const,
};

export function useOverview(days: number) {
  return useQuery({
    queryKey: managerKeys.overview(days),
    queryFn: ({ signal }) => api.get<AnalyticsOverview>(`/analytics/overview${qs({ days })}`, signal),
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
}

export function useFlaggedCustomers() {
  return useQuery({
    queryKey: managerKeys.flagged,
    queryFn: ({ signal }) => api.get<ListResponse<FlaggedCustomer>>("/analytics/flagged-customers", signal),
  });
}

export function useClearFlag() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { customerId: number; note: string; idempotencyKey: string }) =>
      (
        await api.post<ClearFlagResponse>(`/ops/customers/${vars.customerId}/clear-flag`, {
          body: { note: vars.note.trim() },
          idempotencyKey: vars.idempotencyKey,
        })
      ).data,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: managerKeys.flagged });
      void qc.invalidateQueries({ queryKey: ["manager", "overview"] });
    },
  });
}

export function useAudit(entityId: string, limit = 50) {
  return useQuery({
    queryKey: managerKeys.audit(entityId, limit),
    queryFn: ({ signal }) =>
      api.get<ListResponse<AuditEntry>>(`/ops/audit${qs({ entity_id: entityId || undefined, limit })}`, signal),
    placeholderData: keepPreviousData,
  });
}

export function useReconciliation() {
  return useQuery({
    queryKey: managerKeys.recon,
    queryFn: ({ signal }) => api.get<ReconciliationReport>("/ops/reconciliation", signal),
    refetchInterval: 60_000,
  });
}
