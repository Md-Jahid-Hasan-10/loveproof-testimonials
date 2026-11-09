import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { BrandMark } from '../components/BrandMark'
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
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-brand-50 via-canvas to-canvas px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 flex justify-center">
          <Link to="/" className="rounded-lg">
            <BrandMark />
          </Link>
        </div>

        <Card className="p-6" elevation="floating">
          <h1 className="text-xl font-bold tracking-tight text-slate-900">
            Sign in
          </h1>
          <p className="mt-1.5 text-sm leading-relaxed text-slate-600">
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
                className="block text-sm font-semibold tracking-tight text-slate-800"
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
                className="control mt-1.5"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-semibold tracking-tight text-slate-800"
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
                className="control mt-1.5"
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
                className="font-semibold text-brand-700 underline underline-offset-2 hover:text-brand-800"
              >
                Forgot your password?
              </Link>
            </p>
            <p className="text-slate-600">
              No account yet?{' '}
              <Link
                to="/app/register"
                className="font-semibold text-brand-700 underline underline-offset-2 hover:text-brand-800"
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
