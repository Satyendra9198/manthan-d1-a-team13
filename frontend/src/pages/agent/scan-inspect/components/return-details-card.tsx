import { useState, type ReactNode } from 'react'
import { ArrowRight } from 'lucide-react'
import type { ReturnDetails } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { formatPaise } from '@/lib/format'
import { REFUND_METHOD_LABELS, STATUS_LABELS } from '../inspection'
import { Panel } from './panel'

function Field({ label, value, extra }: { label: string; value: string; extra?: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 truncate text-[15px]">{value}</dd>
      {extra}
    </div>
  )
}

export function ReturnDetailsCard({ data }: { data: ReturnDetails }) {
  const [photoIndex, setPhotoIndex] = useState(0)
  const photo = data.photos[photoIndex]
  const photoCaption = `Customer photo · ${photoIndex + 1} of ${data.photos.length}`

  return (
    <Panel aria-labelledby="return-title">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <h2 id="return-title" className="text-lg font-semibold">
          {data.rmaNumber} · {data.item.name}
        </h2>
        <Badge variant="info" data-testid="return-status">
          {data.previousStatus && (
            <>
              {STATUS_LABELS[data.previousStatus]}
              <ArrowRight aria-label="to" />
            </>
          )}
          {STATUS_LABELS[data.status]}
        </Badge>
      </div>

      <dl className="mt-4 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <Field
          label="Customer"
          value={data.customer.name}
          extra={
            data.customer.flaggedForReview && (
              <Badge
                variant="warning"
                className="mt-1.5"
                title="More than 3 returns in 30 days. Check the parcel carefully."
              >
                Flagged · {data.customer.returnsLast30Days} returns in 30 days
              </Badge>
            )
          }
        />
        <Field label="Reason" value={data.reason} />
        <Field label="Refund method" value={REFUND_METHOD_LABELS[data.refundMethod]} />
        <Field label="Item price" value={formatPaise(data.item.pricePaise)} />
      </dl>

      {photo && (
        <button
          type="button"
          onClick={() => setPhotoIndex((i) => (i + 1) % data.photos.length)}
          disabled={data.photos.length < 2}
          aria-label={`${photoCaption}. ${data.photos.length > 1 ? 'Show next photo' : ''}`}
          className="mt-5 flex aspect-[5/3] w-full max-w-xs items-center justify-center overflow-hidden rounded-md bg-muted text-sm text-muted-foreground enabled:cursor-pointer"
        >
          {photo.url ? (
            <img src={photo.url} alt={photo.alt} className="size-full object-cover" />
          ) : (
            photoCaption
          )}
        </button>
      )}

      {data.customerNote && (
        <p className="mt-5 text-sm text-muted-foreground">
          Customer note: “{data.customerNote}”
        </p>
      )}
    </Panel>
  )
}
