import { useNavigate } from 'react-router'
import { rememberTrackEmail } from './track-email'
import { TrackLookupForm } from './track-lookup-form'

/** Customer: "Already started a return? Track it". Route: /track */
export function TrackLookupPage() {
  const navigate = useNavigate()
  return (
    <TrackLookupForm
      onSubmit={(rmaNumber, email) => {
        rememberTrackEmail(rmaNumber, email)
        navigate(`/track/${encodeURIComponent(rmaNumber)}`)
      }}
    />
  )
}
