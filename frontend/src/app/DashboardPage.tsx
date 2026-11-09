import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteSpace, listSpaces } from '../api'
import type { Space } from '../api'
import { describeFailure } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { DashboardAnalyticsPanel } from './DashboardAnalyticsPanel'
import { useCustomer } from './useCustomer'

/**
 * The dashboard: the customer's Spaces and their numbers.
 *
 * Each Space shows its public URL and a link to copy it. The copy button is
 * the one thing here that touches the clipboard, and it says so when the
 * clipboard is not available rather than failing silently.
 *
 * <p>The analytics sit above the Spaces. They answer the question a customer
 * opens the dashboard to ask — how many testimonials have come in — before
 * the list of the Spaces themselves.
 */
export default function DashboardPage() {
  const { customer } = useCustomer()

  const [spaces, setSpaces] = useState<Space[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const loaded = await listSpaces()
      setSpaces(loaded)
      setError(null)
    } catch (cause) {
      setError(describeFailure(cause, 'Your Spaces could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const handleCopy = async (space: Space) => {
    try {
      await navigator.clipboard.writeText(space.publicUrl)
      setCopied(space.id)
      // The confirmation fades on its own, so the interface does not keep a
      // stale "copied" marker next to a Space that has since changed.
      window.setTimeout(() => setCopied(null), 2000)
    } catch {
      setError(
        'Your browser would not let the page copy the link. You can select and copy it by hand.',
      )
    }
  }

  const handleDelete = async (space: Space) => {
    // Deleting a Space destroys its testimonials and its public URL for good,
    // so it is confirmed first.
    const confirmed = window.confirm(
      `Delete “${space.title}”?\n\nThis permanently deletes the Space, every testimonial in it, and its public address. This cannot be undone.`,
    )
    if (!confirmed) {
      return
    }

    try {
      await deleteSpace(space.id)
      setSpaces((current) => current.filter((candidate) => candidate.id !== space.id))
      setError(null)
    } catch (cause) {
      setError(describeFailure(cause, 'That Space could not be deleted.'))
    }
  }

  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-600">
        Loading your Spaces…
      </p>
    )
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">Dashboard</p>
          <h1 className="page-title mt-1.5">Your Spaces</h1>
          <p className="page-subtitle">
            {customer
              ? `Signed in as ${customer.displayName}.`
              : 'Each Space collects testimonials on its own public page.'}
          </p>
        </div>
        <Link to="/app/spaces/new">
          <Button>New Space</Button>
        </Link>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}

      <DashboardAnalyticsPanel />

      {spaces.length === 0 ? (
        <Card className="p-10 text-center" elevation="raised">
          <span
            aria-hidden="true"
            className="mx-auto flex h-12 w-12 items-center justify-center rounded-[0.875rem] bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-brand"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-6 w-6"
            >
              <path d="M20.5 13.5c0 3.6-3.8 6.5-8.5 6.5a10 10 0 0 1-2.6-.34L4 21l1.3-3.7A6.2 6.2 0 0 1 3.5 13.5C3.5 9.9 7.3 7 12 7s8.5 2.9 8.5 6.5Z" />
            </svg>
          </span>
          <h2 className="mt-4 text-lg font-semibold text-slate-900">
            No Spaces yet
          </h2>
          <p className="mx-auto mt-2 max-w-md text-sm leading-relaxed text-slate-600">
            A Space is a page that collects testimonials. You get a public
            address for it, a form to share, and a place to curate what comes
            in.
          </p>
          <Link to="/app/spaces/new" className="mt-6 inline-block">
            <Button>Create your first Space</Button>
          </Link>
        </Card>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {spaces.map((space) => (
            <li key={space.id}>
              <Card className="flex h-full flex-col p-5" elevation="raised">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="truncate text-base font-semibold text-slate-900">
                      <Link
                        to={`/app/spaces/${space.id}`}
                        className="transition hover:text-brand-700 hover:underline"
                      >
                        {space.title}
                      </Link>
                    </h2>
                    {space.subtitle && (
                      <p className="mt-1 truncate text-sm text-slate-600">
                        {space.subtitle}
                      </p>
                    )}
                  </div>
                  {!space.editable && (
                    <span className="pill shrink-0 bg-amber-100 text-amber-900">
                      Read-only
                    </span>
                  )}
                </div>

                <p className="surface-sunken mt-4 truncate px-3 py-2 font-mono text-xs text-slate-700">
                  {space.publicUrl}
                </p>

                <div className="mt-4 flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => void handleCopy(space)}
                  >
                    {copied === space.id ? 'Copied' : 'Copy link'}
                  </Button>
                  <Link to={`/app/spaces/${space.id}`}>
                    <Button variant="ghost" size="sm">
                      Open
                    </Button>
                  </Link>
                  {space.editable && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-rose-700 hover:bg-rose-50"
                      onClick={() => void handleDelete(space)}
                    >
                      Delete
                    </Button>
                  )}
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
