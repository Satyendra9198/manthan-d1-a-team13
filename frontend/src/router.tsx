import { createBrowserRouter } from "react-router";
import type { ComponentType } from "react";
import { RequireAuth } from "./auth/RequireAuth";
import { PortalLayout } from "./layouts/PortalLayout";
import { StaffLayout } from "./layouts/StaffLayout";
import { RouteError, NotFound } from "./pages/public/Forbidden";
import { Landing } from "./pages/public/Landing";
import { StaffLogin } from "./pages/public/StaffLogin";

/** Route-level code splitting: each page chunk loads on demand (Recharts only ships with the dashboard). */
function page<M>(loader: () => Promise<M>, pick: (m: M) => ComponentType) {
  return async () => ({ Component: pick(await loader()) });
}

export const router = createBrowserRouter([
  { path: "/", element: <Landing />, errorElement: <RouteError /> },
  { path: "/login", element: <StaffLogin />, errorElement: <RouteError /> },
  {
    path: "/portal",
    element: <RequireAuth roles={["CUSTOMER"]} />,
    errorElement: <RouteError />,
    children: [
      {
        element: <PortalLayout />,
        children: [
          { index: true, lazy: page(() => import("./pages/portal/Orders"), (m) => m.Orders) },
          { path: "orders/:orderNumber", lazy: page(() => import("./pages/portal/OrderDetail"), (m) => m.OrderDetail) },
          { path: "orders/:orderNumber/return", lazy: page(() => import("./pages/portal/ReturnWizard"), (m) => m.ReturnWizard) },
          { path: "returns", lazy: page(() => import("./pages/portal/ReturnsList"), (m) => m.ReturnsList) },
          { path: "returns/:id", lazy: page(() => import("./pages/portal/ReturnDetail"), (m) => m.ReturnDetail) },
          { path: "returns/:id/label", lazy: page(() => import("./pages/portal/LabelPage"), (m) => m.LabelPage) },
          { path: "notifications", lazy: page(() => import("./pages/portal/Notifications"), (m) => m.Notifications) },
        ],
      },
    ],
  },
  {
    element: <RequireAuth roles={["WAREHOUSE_AGENT", "FINANCE_REVIEWER", "OPS_MANAGER", "ADMIN"]} />,
    errorElement: <RouteError />,
    children: [
      {
        element: <StaffLayout />,
        children: [
          {
            path: "/warehouse",
            element: <RequireAuth roles={["WAREHOUSE_AGENT"]} />,
            children: [
              { index: true, lazy: page(() => import("./pages/warehouse/ScanStation"), (m) => m.ScanStation) },
              { path: "queue", lazy: page(() => import("./pages/warehouse/WarehouseQueue"), (m) => m.WarehouseQueue) },
              { path: "returns/:id", lazy: page(() => import("./pages/warehouse/Inspection"), (m) => m.Inspection) },
            ],
          },
          {
            path: "/finance",
            element: <RequireAuth roles={["FINANCE_REVIEWER"]} />,
            children: [
              { index: true, lazy: page(() => import("./pages/finance/FinanceQueue"), (m) => m.FinanceQueue) },
              { path: "returns/:id", lazy: page(() => import("./pages/finance/FinanceReview"), (m) => m.FinanceReview) },
            ],
          },
          {
            path: "/manager",
            element: <RequireAuth roles={["OPS_MANAGER"]} />,
            children: [
              { index: true, lazy: page(() => import("./pages/manager/Dashboard"), (m) => m.Dashboard) },
              { path: "flagged", lazy: page(() => import("./pages/manager/FlaggedCustomers"), (m) => m.FlaggedCustomers) },
              { path: "audit", lazy: page(() => import("./pages/manager/AuditLog"), (m) => m.AuditLog) },
              { path: "reconciliation", lazy: page(() => import("./pages/manager/Reconciliation"), (m) => m.Reconciliation) },
            ],
          },
        ],
      },
    ],
  },
  { path: "*", element: <NotFound /> },
]);
