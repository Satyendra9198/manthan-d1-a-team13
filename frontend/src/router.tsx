import { Navigate, type RouteObject } from 'react-router'
import { AppLayout } from '@/components/layout/app-layout'
import { CustomerLayout } from '@/components/layout/customer-layout'
import { DEFAULT_PATH } from '@/config/navigation'
import { AuditLogPage } from '@/pages/audit-log-page'
import { DashboardPage } from '@/pages/dashboard-page'
import { FinanceQueuePage } from '@/pages/finance/finance-queue/finance-queue-page'
import { ReturnStatusPage } from '@/pages/customer/return-status/return-status-page'
import { RefundsLedgerPage } from '@/pages/finance/refunds-ledger/refunds-ledger-page'
import { ScanInspectPage } from '@/pages/agent/scan-inspect/scan-inspect-page'
import { CustomerWizard } from '@/pages/customer-wizard/customer-wizard'
import { ReturnStatusRoute } from '@/pages/return-status/return-status-page'
import { TrackReturnPage } from '@/pages/track-return/track-return-page'

export const routes: RouteObject[] = [
  // Customer screens (no staff sidebar).
  {
    path: '/track',
    element: <CustomerLayout />,
    children: [{ path: ':rmaNumber', element: <ReturnStatusPage /> }],
  },
  // Customer return flow: start a return, look one up, see its status.
  // These pages bring their own header (with Start a return / Track a return links).
  { path: '/returns/new', element: <CustomerWizard /> },
  { path: '/returns/track', element: <TrackReturnPage /> },
  { path: '/returns/:rmaNumber', element: <ReturnStatusRoute /> },
  // Staff screens.
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
