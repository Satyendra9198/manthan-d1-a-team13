import { useId, useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type Props = {
  onFind: (qrCode: string) => void
  loading: boolean
  error?: string
}

export function QrLookupForm({ onFind, loading, error }: Props) {
  const id = useId()
  const [code, setCode] = useState('')
  const [cameraNote, setCameraNote] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!code.trim()) return inputRef.current?.focus()
    onFind(code)
  }

  const message = error ?? (cameraNote ? 'Camera scanning is not available yet. Type the code instead.' : null)

  return (
    <form onSubmit={handleSubmit} className="grid gap-2">
      <Label htmlFor={id}>Scan or enter QR code</Label>
      <div className="flex flex-col gap-3 sm:flex-row">
        <Input
          ref={inputRef}
          id={id}
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="rr-qr-7f3a9c41"
          autoComplete="off"
          autoFocus
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? `${id}-msg` : undefined}
          className="flex-1"
        />
        <div className="flex gap-3">
          <Button variant="outline" onClick={() => setCameraNote(true)}>
            Use camera
          </Button>
          <Button type="submit" disabled={loading}>
            {loading ? 'Finding…' : 'Find return'}
          </Button>
        </div>
      </div>
      {message && (
        <p
          id={`${id}-msg`}
          role={error ? 'alert' : undefined}
          className={error ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}
        >
          {message}
        </p>
      )}
    </form>
  )
}
