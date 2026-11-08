import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { useCustomer } from './useCustomer'

/**
 * The customer's sign-in page.
 *
 * The failure message comes from the server word for word. The server gives
 * the same message for a wrong password and for an unknown address on
 * purpose, and rewriting it here would undo that.
 */
export default function CustomerSignInPage() {
  const { signIn, error } = useCustomer()
  const navigate = useNavigate()
  const location = useLocation()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const destination =
    (location.state as { from?: string } | null)?.from ?? '/app'

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError(null)
    setSubmitting(true)
    try {
      const signedIn = await signIn(email, password)
      if (signedIn) {
        navigate(destination, { replace: true })
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <Link
            to="/"
            className="text-lg font-bold tracking-tight text-slate-900"
          >
            LoveProof
          </Link>
        </div>

        <Card className="p-6">
          <h1 className="text-xl font-bold tracking-tight text-slate-900">
            Sign in
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Welcome back. Sign in to manage your Spaces.
          </p>

          {(formError || error) && (
            <div className="mt-4">
              <ErrorBanner
                title="Sign-in failed"
                message={formError ?? error ?? ''}
              />
            </div>
          )}

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-slate-700"
              >
                Email address
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-slate-700"
              >
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            </div>

            <Button type="submit" loading={submitting} className="w-full">
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <div className="mt-6 space-y-2 border-t border-slate-200 pt-4 text-sm">
            <p>
              <Link
                to="/app/forgot-password"
                className="font-medium text-brand-700 underline"
              >
                Forgot your password?
              </Link>
            </p>
            <p className="text-slate-600">
              No account yet?{' '}
              <Link
                to="/app/register"
                className="font-medium text-brand-700 underline"
              >
                Create one
              </Link>
              .
            </p>
          </div>
        </Card>
      </div>
    </div>
  )
}
