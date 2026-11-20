import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouterProvider } from "react-router";
import { onUnauthorized, isApiError } from "./api/client";
import { AppToaster } from "./components/AppToaster";
import "./index.css";
import { initTheme } from "./lib/theme";
import { router } from "./router";

initTheme();

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // Never retry client errors (4xx); a couple of retries for network/5xx on reads only.
      retry: (count, err) => !(isApiError(err) && err.status >= 400 && err.status < 500) && count < 2,
    },
    mutations: { retry: false },
  },
});

onUnauthorized((loginPath) => {
  queryClient.clear();
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  void router.navigate(`${loginPath}?next=${next}`, { replace: true });
});

const root = document.getElementById("root");
if (!root) throw new Error("#root missing");

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <AppToaster />
    </QueryClientProvider>
  </StrictMode>,
);
