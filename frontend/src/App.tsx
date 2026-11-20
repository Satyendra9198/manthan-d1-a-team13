import { useRoute } from '@/lib/hash-route'
import { CustomerWizard } from '@/pages/customer-wizard/customer-wizard'
import { ReturnStatusPage } from '@/pages/return-status/return-status-page'
import { TrackReturnPage } from '@/pages/track-return/track-return-page'

/** Picks the page from the URL hash; see lib/hash-route.ts for the paths. */
export default function App() {
  const route = useRoute()

  switch (route.page) {
    case 'track-return':
      return <TrackReturnPage />
    case 'return-status':
      return <ReturnStatusPage key={route.rmaNumber} rmaNumber={route.rmaNumber} />
    default:
      return <CustomerWizard />
  }
}
