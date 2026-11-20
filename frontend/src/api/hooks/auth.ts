import { useMutation, useQueryClient } from "@tanstack/react-query";
import { clearSession, setSession } from "../../auth/session";
import { api } from "../client";
import type { AuthResponse, CustomerLookupRequest, StaffLoginRequest } from "../types";

export function useCustomerLookup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CustomerLookupRequest) =>
      (await api.post<AuthResponse>("/auth/customer/lookup", { body, skipAuthRedirect: true })).data,
    onSuccess: (auth) => {
      qc.clear();
      setSession(auth);
    },
  });
}

export function useStaffLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: StaffLoginRequest) =>
      (await api.post<AuthResponse>("/auth/staff/login", { body, skipAuthRedirect: true })).data,
    onSuccess: (auth) => {
      qc.clear();
      setSession(auth);
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      try {
        await api.post<null>("/auth/logout", { skipAuthRedirect: true });
      } catch {
        /* the token is dropped locally regardless */
      }
    },
    onSettled: () => {
      clearSession();
      qc.clear();
    },
  });
}
