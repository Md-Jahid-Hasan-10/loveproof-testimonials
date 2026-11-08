import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, getDashboardStats } from '../api'
import type { DashboardStats } from '../api'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'

/**
 * The admin dashboard: the moderation counts at a glance.
 *
 * Every number comes from one request to `/api/admin/testimonials/stats`, so
 * the tiles can never disagree with each other.
 */
export default function AdminDashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setStats(await getDashboardStats())
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) {
        setError('Your session has expired. Sign in again to continue.')
      } else {
        setError(
          cause instanceof Error
            ? cause.message
            : 'The dashboard could not be loaded.',
        )
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Dashboard
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Every testimonial submitted through the public form, by moderation
          state.
        </p>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}

      {loading && !stats && (
        <p role="status" aria-live="polite" className="text-sm text-slate-600">
          Loading the dashboard…
        </p>
      )}

      {stats && (
        <>
          <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <CountTile label="Pending" value={stats.pending} tone="amber" />
            <CountTile label="Approved" value={stats.approved} tone="emerald" />
            <CountTile label="Rejected" value={stats.rejected} tone="rose" />
            <CountTile label="Deleted" value={stats.deleted} tone="slate" />
          </dl>

          <div className="grid gap-4 lg:grid-cols-3">
            <Card className="p-5">
              <p className="text-sm font-medium text-slate-600">
                Total testimonials
              </p>
              <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
                {stats.total}
              </p>
              <p className="mt-1 text-sm text-slate-500">
                Excluding the {stats.deleted} deleted.
              </p>
            </Card>

            <Card className="p-5">
              <p className="text-sm font-medium text-slate-600">
                Submitted in the last 7 days
              </p>
              <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
                {stats.last7Days}
              </p>
            </Card>

            <Card className="p-5">
              <p className="text-sm font-medium text-slate-600">
                Average approved rating
              </p>
              <p className="mt-2 text-3xl font-bold tracking-tight text-slate-900">
                {stats.averageRating === null
                  ? '—'
                  : stats.averageRating.toFixed(1)}
              </p>
              {stats.averageRating === null && (
                <p className="mt-1 text-sm text-slate-500">
                  No approved testimonials yet.
                </p>
              )}
            </Card>
          </div>

          <div>
            <Link
              to="/admin/inbox"
              className="inline-flex items-center rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
            >
              Open the moderation inbox
            </Link>
          </div>
        </>
      )}
    </div>
  )
}

/** The visual tone of a count tile. */
type Tone = 'amber' | 'emerald' | 'rose' | 'slate'

const TONE_CLASSES: Record<Tone, string> = {
  amber: 'text-amber-700 bg-amber-50 ring-amber-200',
  emerald: 'text-emerald-700 bg-emerald-50 ring-emerald-200',
  rose: 'text-rose-700 bg-rose-50 ring-rose-200',
  slate: 'text-slate-700 bg-slate-50 ring-slate-200',
}

/**
 * A single count, as a definition list term so a screen reader announces the
 * label together with the number.
 */
function CountTile({
  label,
  value,
  tone,
}: {
  label: string
  value: number
  tone: Tone
}) {
  return (
    <Card className="p-5">
      <dt className="text-sm font-medium text-slate-600">{label}</dt>
      <dd
        className={`mt-2 inline-flex rounded-lg px-2.5 py-1 text-3xl font-bold tracking-tight ring-1 ring-inset ${TONE_CLASSES[tone]}`}
      >
        {value}
      </dd>
    </Card>
  )
}
