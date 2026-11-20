import { X } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { ACTOR_FILTERS, AUDIT_ACTIONS, DATE_FILTERS } from '../audit'
import type { Filters } from '../use-audit-log'

type Props = {
  filters: Filters
  customerName: string | null
  onChange: (name: keyof Filters, value: string) => void
}

export function AuditFilters({ filters, customerName, onChange }: Props) {
  return (
    <div className="space-y-3">
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="grid gap-2">
          <Label htmlFor="audit-rma">RMA</Label>
          <Input
            id="audit-rma"
            type="search"
            placeholder="RMA-2026-00731"
            value={filters.rma}
            onChange={(e) => onChange('rma', e.target.value)}
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="audit-actor">Actor</Label>
          <NativeSelect id="audit-actor" value={filters.actor} onChange={(e) => onChange('actor', e.target.value)}>
            <option value="">All actors</option>
            {ACTOR_FILTERS.map((a) => (
              <option key={a.value} value={a.value}>
                {a.label}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="audit-action">Action</Label>
          <NativeSelect id="audit-action" value={filters.action} onChange={(e) => onChange('action', e.target.value)}>
            <option value="">All actions</option>
            {AUDIT_ACTIONS.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="audit-date">Date</Label>
          <NativeSelect id="audit-date" value={filters.days} onChange={(e) => onChange('days', e.target.value)}>
            {DATE_FILTERS.map((d) => (
              <option key={d.value} value={d.value}>
                {d.label}
              </option>
            ))}
          </NativeSelect>
        </div>
      </div>
      {filters.customer && (
        <button
          type="button"
          onClick={() => onChange('customer', '')}
          className="inline-flex cursor-pointer items-center gap-1.5 rounded-full border bg-background px-3 py-1 text-sm hover:bg-muted"
        >
          Customer: {customerName ?? filters.customer}
          <X className="size-3.5" aria-hidden />
          <span className="sr-only">Remove customer filter</span>
        </button>
      )}
    </div>
  )
}
