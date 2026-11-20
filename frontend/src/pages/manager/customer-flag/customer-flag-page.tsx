import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { Badge } from '@/components/ui/badge'
import { StatTile } from '../dashboard/components/panel'
import { ReturnsTable } from './components/returns-table'
import { ReviewFlagPanel } from './components/review-flag-panel'
import { customerLine, flagBadge, statTiles } from './customer-flag'
import { useCustomerProfile } from './use-customer-profile'

/** Operations manager: one customer's returns and the flag review. Route: /dashboard/customers/:customerId */
export function CustomerFlagPage() {
  const { customerId = '' } = useParams()
  const c = useCustomerProfile(customerId)

  return (
    <div className="space-y-5">
      <Link
        to="/dashboard"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" />
        Back to dashboard
      </Link>

      {c.loading && <p className="text-sm text-muted-foreground">Loading customer…</p>}
      {c.error && (
        <p role="alert" className="rounded-md bg-danger px-4 py-3 text-sm text-danger-foreground">
          {c.error}
        </p>
      )}

      {c.profile && (
        <>
          <header className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h1 className="text-[26px] leading-tight font-medium tracking-tight">{c.profile.name}</h1>
              <p className="mt-1 text-[15px] text-muted-foreground">{customerLine(c.profile)}</p>
            </div>
            <Badge variant={flagBadge(c.profile).variant} data-testid="flag-badge">
              {flagBadge(c.profile).label}
            </Badge>
          </header>

          {c.saved && (
            <p role="status" className="rounded-md bg-success px-4 py-3 text-sm text-success-foreground">
              {c.saved}
            </p>
          )}

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {statTiles(c.profile).map((t) => (
              <StatTile key={t.label} {...t} />
            ))}
          </div>

          <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_340px]">
            <ReturnsTable returns={c.profile.returns} />
            <ReviewFlagPanel profile={c.profile} saving={c.saving} error={c.saveError} onReview={c.review} />
          </div>
        </>
      )}
    </div>
  )
}
