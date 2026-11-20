import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '@/api/client'
import { authApi } from '@/api/auth'
import { setSession, useSession } from '@/auth/session'
import { Button } from '@/components/ui/button'
import { TextField } from '@/components/ui/text-field'
import { canOpen, homePathFor } from '@/config/navigation'
import type { StaffRole } from '@/auth/session'

/** Only same-app paths the role may open; anything else lands on the role's first screen. */
function destination(role: StaffRole, next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') && canOpen(role, next)
    ? next
    : homePathFor(role)
}

/** Figma "Staff – Login": staff sign in with their work email. Route: /login */
export function LoginPage() {
  const session = useSession()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (session) return <Navigate to={destination(session.staff.role, params.get('next'))} replace />

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (submitting) return
    if (!email.trim() || !password) {
      setError('Enter your work email and password.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const signedIn = await authApi.login({ email: email.trim(), password })
      setSession(signedIn)
      navigate(destination(signedIn.staff.role, params.get('next')), { replace: true })
    } catch (e) {
      setError(
        e instanceof ApiError ? e.message : 'Connection problem. Check your network and try again.',
      )
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-svh items-center justify-center bg-neutral-200/70 px-4 dark:bg-neutral-950">
      <main className="w-full max-w-sm rounded-2xl border bg-background p-6 shadow-sm">
        <p className="text-[15px] font-semibold tracking-tight">ReturnRight</p>
        <h1 className="mt-6 text-xl font-semibold">Staff sign in</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          For warehouse, finance and operations staff.
        </p>

        <form className="mt-6 grid gap-4" onSubmit={onSubmit} noValidate>
          <TextField
            label="Work email"
            type="email"
            autoComplete="username"
            placeholder="name@returnright.example"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoFocus
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && (
            <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {error}
            </p>
          )}
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>
      </main>
    </div>
  )
}
