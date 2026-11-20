import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, qs } from "../client";
import type { InspectionRequest, ListResponse, ReturnDTO, ReturnStatus, ScanResponse } from "../types";
import { handleReturnMutationError } from "./common";

export const warehouseKeys = {
  all: ["warehouse"] as const,
  queue: (status: string) => ["warehouse", "queue", status] as const,
  ret: (id: string) => ["warehouse", "returns", id] as const,
};

export function useWarehouseQueue(statuses: ReturnStatus[]) {
  const status = statuses.join(",");
  return useQuery({
    queryKey: warehouseKeys.queue(status),
    queryFn: ({ signal }) => api.get<ListResponse<ReturnDTO>>(`/warehouse/returns${qs({ status })}`, signal),
    refetchInterval: 30_000,
  });
}

export function useWarehouseReturn(id: string) {
  return useQuery({
    queryKey: warehouseKeys.ret(id),
    queryFn: ({ signal }) => api.get<ReturnDTO>(`/warehouse/returns/${encodeURIComponent(id)}`, signal),
  });
}

/** Scan is safe to repeat: a second scan returns `already_received=true` (T4 / F-06). Errors are rendered by the caller. */
export function useScan() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (qrToken: string) => (await api.post<ScanResponse>("/warehouse/scan", { body: { qr_token: qrToken } })).data,
    onSuccess: (res) => {
      qc.setQueryData(warehouseKeys.ret(res.return.id), res.return);
      void qc.invalidateQueries({ queryKey: ["warehouse", "queue"] });
    },
  });
}

/** PUT one unit's inspection (T7/T8). Version changes per call, so callers chain the returned DTO. */
export function useSaveInspection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { returnId: string; itemId: string; version: number; body: InspectionRequest }) =>
      (
        await api.put<ReturnDTO>(
          `/warehouse/returns/${encodeURIComponent(vars.returnId)}/items/${encodeURIComponent(vars.itemId)}/inspection`,
          { body: vars.body, ifMatch: vars.version },
        )
      ).data,
    onSuccess: (ret) => qc.setQueryData(warehouseKeys.ret(ret.id), ret),
  });
}

export function useCompleteInspection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { ret: ReturnDTO; idempotencyKey: string }) =>
      (
        await api.post<ReturnDTO>(`/warehouse/returns/${encodeURIComponent(vars.ret.id)}/complete-inspection`, {
          idempotencyKey: vars.idempotencyKey,
          ifMatch: vars.ret.version,
        })
      ).data,
    onSuccess: (ret) => {
      qc.setQueryData(warehouseKeys.ret(ret.id), ret);
      void qc.invalidateQueries({ queryKey: ["warehouse", "queue"] });
    },
    onError: (err, vars) => handleReturnMutationError(err, qc, [warehouseKeys.ret(vars.ret.id)]),
  });
}
