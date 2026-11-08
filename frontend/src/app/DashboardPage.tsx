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
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Your Spaces
          </h1>
          <p className="mt-1 text-sm text-slate-600">
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
        <Card className="p-8 text-center">
          <h2 className="text-lg font-semibold text-slate-900">
            No Spaces yet
          </h2>
          <p className="mx-auto mt-2 max-w-md text-sm text-slate-600">
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
              <Card className="flex h-full flex-col p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="truncate text-base font-semibold text-slate-900">
                      <Link
                        to={`/app/spaces/${space.id}`}
                        className="hover:text-brand-700 hover:underline"
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
                    <span className="shrink-0 rounded-md bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-900">
                      Read-only
                    </span>
                  )}
                </div>

                <p className="mt-4 truncate rounded-lg bg-slate-50 px-3 py-2 font-mono text-xs text-slate-700">
                  {space.publicUrl}
                </p>

                <div className="mt-4 flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => void handleCopy(space)}
                  >
                    {copied === space.id ? 'Copied' : 'Copy link'}
                  </Button>
                  <Link to={`/app/spaces/${space.id}`}>
                    <Button variant="ghost">Open</Button>
                  </Link>
                  {space.editable && (
                    <Button
                      variant="ghost"
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
