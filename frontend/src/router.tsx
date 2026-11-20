import { Navigate, type RouteObject } from 'react-router'
import { AppLayout } from '@/components/layout/app-layout'
import { DEFAULT_PATH } from '@/config/navigation'
import { AuditLogPage } from '@/pages/audit-log-page'
import { DashboardPage } from '@/pages/dashboard-page'
import { FinanceQueuePage } from '@/pages/finance-queue-page'
import { RefundsLedgerPage } from '@/pages/refunds-ledger-page'
import { ScanInspectPage } from '@/pages/scan-inspect-page'

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <Navigate to={DEFAULT_PATH} replace /> },
      { path: 'scan', element: <ScanInspectPage /> },
      { path: 'finance', element: <FinanceQueuePage /> },
      { path: 'refunds', element: <RefundsLedgerPage /> },
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'audit', element: <AuditLogPage /> },
      { path: '*', element: <Navigate to={DEFAULT_PATH} replace /> },
    ],
  },
]
