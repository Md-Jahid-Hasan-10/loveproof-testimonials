import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { useCustomer } from './useCustomer'

/**
 * The customer's registration page.
 *
 * The account is created unverified: the backend emails a verification link,
 * and the page says so rather than pretending the account is fully ready.
 */
export default function CustomerRegisterPage() {
  const { register, error } = useCustomer()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setFormError(null)

    if (password.length < 8) {
      setFormError('A password must be at least 8 characters long.')
      return
    }

    setSubmitting(true)
    try {
      const created = await register(
        email,
        password,
        displayName.trim() === '' ? null : displayName.trim(),
      )
      if (created) {
        navigate('/app', { replace: true })
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
            Create your account
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Start collecting testimonials in a couple of minutes.
          </p>

          {(formError || error) && (
            <div className="mt-4">
              <ErrorBanner
                title="Registration failed"
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
                htmlFor="displayName"
                className="block text-sm font-medium text-slate-700"
              >
                Your name
              </label>
              <input
                id="displayName"
                name="displayName"
                type="text"
                autoComplete="name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                aria-describedby="displayNameHelp"
                className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
              <p id="displayNameHelp" className="mt-1 text-xs text-slate-500">
                Optional. We use the first part of your email address if you
                leave this blank.
              </p>
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
                autoComplete="new-password"
                required
                minLength={8}
                aria-describedby="passwordHelp"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
              <p id="passwordHelp" className="mt-1 text-xs text-slate-500">
                At least 8 characters.
              </p>
            </div>

            <Button type="submit" loading={submitting} className="w-full">
              {submitting ? 'Creating your account…' : 'Create account'}
            </Button>
          </form>

          <p className="mt-6 border-t border-slate-200 pt-4 text-sm text-slate-600">
            Already have an account?{' '}
            <Link
              to="/app/sign-in"
              className="font-medium text-brand-700 underline"
            >
              Sign in
            </Link>
            .
          </p>
        </Card>
      </div>
    </div>
  )
}
