import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { useAuth } from './useAuth'

/**
 * The admin sign-in page.
 *
 * A wrong username and a wrong password fail the same way with the same
 * message, so the page never reveals which accounts exist.
 */
export default function AdminLoginPage() {
  const { admin, loading, error, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // The page the visitor was trying to reach before being sent here.
  const from =
    (location.state as { from?: string } | null)?.from ?? '/admin'

  if (!loading && admin !== null) {
    return <Navigate to={from} replace />
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmitting(true)
    const succeeded = await signIn(username, password)
    setSubmitting(false)
    if (succeeded) {
      navigate(from, { replace: true })
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-slate-100 via-canvas to-canvas px-4">
      {/*
        The skip link is the first focusable element, so a keyboard user can
        jump straight to the form without reading the page heading first.
      */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to the sign-in form
      </a>

      <main id="main" tabIndex={-1} className="w-full max-w-md focus:outline-none">
        <Card className="p-6 sm:p-8" elevation="floating">
          <div className="mb-6">
            <p className="eyebrow">LoveProof Admin</p>
            <h1 className="mt-1.5 text-2xl font-bold tracking-tight text-slate-900">
              Admin sign in
            </h1>
            <p className="mt-2 text-sm leading-relaxed text-slate-600">
              Sign in to moderate customer testimonials.
            </p>
          </div>

          {error && (
            <div className="mb-4">
              <ErrorBanner
                title="Sign-in failed"
                message={error}
                onDismiss={() => undefined}
              />
            </div>
          )}

          <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
            <Field label="Username" required>
              {(props) => (
                <input
                  {...props}
                  type="text"
                  name="username"
                  autoComplete="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  className="control"
                />
              )}
            </Field>

            <Field label="Password" required>
              {(props) => (
                <input
                  {...props}
                  type="password"
                  name="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="control"
                />
              )}
            </Field>

            <Button type="submit" loading={submitting} className="w-full">
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </Card>
      </main>
    </div>
  )
}
