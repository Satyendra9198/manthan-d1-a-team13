import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'

export function ScanInspectPage() {
  return (
    <PageHeader
      title="Scan and inspect"
      description="Scan the parcel QR or type the code, then grade and decide."
      actions={<Badge>Today: 37 inspected</Badge>}
    />
  )
}
