import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../client";
import type {
  CreateReturnRequest,
  ListResponse,
  NotificationDTO,
  OrderDetail,
  OrderSummary,
  ReturnDTO,
  ReturnLabel,
} from "../types";
import { handleReturnMutationError } from "./common";

export const customerKeys = {
  orders: ["customer", "orders"] as const,
  order: (n: string) => ["customer", "orders", n] as const,
  returns: ["customer", "returns"] as const,
  ret: (id: string) => ["customer", "returns", id] as const,
  label: (id: string) => ["customer", "returns", id, "label"] as const,
  notifications: ["customer", "notifications"] as const,
};

export function useOrders() {
  return useQuery({
    queryKey: customerKeys.orders,
    queryFn: ({ signal }) => api.get<ListResponse<OrderSummary>>("/customer/orders", signal),
  });
}

export function useOrder(orderNumber: string) {
  return useQuery({
    queryKey: customerKeys.order(orderNumber),
    queryFn: ({ signal }) => api.get<OrderDetail>(`/customer/orders/${encodeURIComponent(orderNumber)}`, signal),
  });
}

export function useMyReturns() {
  return useQuery({
    queryKey: customerKeys.returns,
    queryFn: ({ signal }) => api.get<ListResponse<ReturnDTO>>("/customer/returns", signal),
  });
}

export function useMyReturn(id: string) {
  return useQuery({
    queryKey: customerKeys.ret(id),
    queryFn: ({ signal }) => api.get<ReturnDTO>(`/customer/returns/${encodeURIComponent(id)}`, signal),
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === "REFUND_PENDING" ? 5000 : false;
    },
  });
}

export function useLabel(id: string) {
  return useQuery({
    queryKey: customerKeys.label(id),
    queryFn: ({ signal }) => api.get<ReturnLabel>(`/customer/returns/${encodeURIComponent(id)}/label`, signal),
    staleTime: 60_000,
  });
}

export function useNotifications() {
  return useQuery({
    queryKey: customerKeys.notifications,
    queryFn: ({ signal }) => api.get<ListResponse<NotificationDTO>>("/customer/notifications", signal),
    refetchInterval: 30_000,
  });
}

export function useCreateReturn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { body: CreateReturnRequest; idempotencyKey: string }) =>
      (await api.post<ReturnDTO>("/customer/returns", { body: vars.body, idempotencyKey: vars.idempotencyKey })).data,
    onSuccess: (ret) => {
      qc.setQueryData(customerKeys.ret(ret.id), ret);
      void qc.invalidateQueries({ queryKey: ["customer"] });
    },
  });
}

function useReturnAction(action: "ship" | "cancel") {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (vars: { ret: ReturnDTO }) =>
      (await api.post<ReturnDTO>(`/customer/returns/${encodeURIComponent(vars.ret.id)}/${action}`, { ifMatch: vars.ret.version }))
        .data,
    onSuccess: (ret) => {
      qc.setQueryData(customerKeys.ret(ret.id), ret);
      void qc.invalidateQueries({ queryKey: customerKeys.returns, exact: true });
      void qc.invalidateQueries({ queryKey: customerKeys.orders });
      void qc.invalidateQueries({ queryKey: customerKeys.notifications });
    },
    onError: (err, vars) => handleReturnMutationError(err, qc, [customerKeys.ret(vars.ret.id)]),
  });
}

export const useShipReturn = () => useReturnAction("ship");
export const useCancelReturn = () => useReturnAction("cancel");
