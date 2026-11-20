import { useEffect, useState } from 'react'
import { ApiError } from '@/api/client'
import { dashboardApi } from '@/api/dashboard'
import type { DashboardPeriodDays, DashboardResponse } from '@/api/types'

export function useDashboard() {
  const [days, setDays] = useState<DashboardPeriodDays>(30)
  const [result, setResult] = useState<
    { days: DashboardPeriodDays; data: DashboardResponse } | { days: DashboardPeriodDays; error: string } | null
  >(null)

  useEffect(() => {
    let cancelled = false
    dashboardApi
      .getDashboard(days)
      .then((data) => !cancelled && setResult({ days, data }))
      .catch(
        (e) =>
          !cancelled &&
          setResult({ days, error: e instanceof ApiError ? e.message : "Couldn't load the dashboard. Try again." }),
      )
    return () => {
      cancelled = true
    }
  }, [days])

  // Keep showing the previous data while a new period loads (no layout jump).
  const data = result && 'data' in result ? result.data : null
  const error = result && 'error' in result && result.days === days ? result.error : null
  const refreshing = result?.days !== days

  return { days, setDays, data, error, refreshing }
}
