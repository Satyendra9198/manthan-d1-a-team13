import { ScanLine } from 'lucide-react'
import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { InspectionPanel } from './components/inspection-panel'
import { PartialAcceptDialog } from './components/partial-accept-dialog'
import { Panel } from './components/panel'
import { QrLookupForm } from './components/qr-lookup-form'
import { RejectDialog } from './components/reject-dialog'
import { ReturnDetailsCard } from './components/return-details-card'
import { STATIC_SCAN_INSPECT } from './static-data'
import { useScanInspect, type ScanInspectInitial } from './use-scan-inspect'

/** Agent screen: scan a parcel, grade its condition, accept / partially accept / reject. */
export function ScanInspectPage({
  initial = STATIC_SCAN_INSPECT,
}: {
  /** Pre-filled data; defaults to the static design data. Pass {} to start empty. */
  initial?: ScanInspectInitial
}) {
  const s = useScanInspect(initial)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Scan and inspect"
        description="Scan the parcel QR or type the code, then grade and decide."
        actions={
          s.inspectedToday !== null && <Badge>Today: {s.inspectedToday} inspected</Badge>
        }
      />

      <QrLookupForm
        onFind={s.findReturn}
        loading={s.lookup.status === 'loading'}
        error={s.lookup.status === 'error' ? s.lookup.message : undefined}
      />

      {s.result && (
        <p
          role="status"
          className={cn(
            'rounded-md px-4 py-3 text-sm',
            s.result.kind === 'success'
              ? 'bg-success text-success-foreground'
              : 'bg-danger text-danger-foreground',
          )}
        >
          {s.result.message}
        </p>
      )}

      {s.lookup.status === 'loaded' && s.config ? (
        <div className="grid items-start gap-6 lg:grid-cols-2">
          <ReturnDetailsCard key={s.lookup.data.id} data={s.lookup.data} />
          <InspectionPanel
            data={s.lookup.data}
            config={s.config}
            grade={s.grade}
            onGradeChange={s.setGrade}
            notes={s.notes}
            onNotesChange={s.setNotes}
            submitting={s.submitting}
            shortcutsEnabled={s.dialog === null}
            onAccept={s.accept}
            onPartialAccept={() => s.openDialog('partial_accept')}
            onReject={() => s.openDialog('reject')}
          />

          <RejectDialog
            open={s.dialog === 'reject'}
            data={s.lookup.data}
            defaultNotes={s.notes}
            submitting={s.submitting === 'reject'}
            serverError={s.dialogError}
            onSubmit={s.reject}
            onClose={s.closeDialog}
          />
          {s.grade && (
            <PartialAcceptDialog
              open={s.dialog === 'partial_accept'}
              data={s.lookup.data}
              grade={s.grade}
              financeApprovalAbovePaise={s.config.financeApprovalAbovePaise}
              defaultNotes={s.notes}
              submitting={s.submitting === 'partial_accept'}
              serverError={s.dialogError}
              onSubmit={s.partialAccept}
              onClose={s.closeDialog}
            />
          )}
        </div>
      ) : (
        s.lookup.status !== 'loading' && (
          <Panel className="flex flex-col items-center gap-2 py-14 text-center text-muted-foreground">
            <ScanLine className="size-8" />
            <p className="text-sm">Scan a parcel or enter its QR code to start an inspection.</p>
          </Panel>
        )
      )}
    </div>
  )
}
