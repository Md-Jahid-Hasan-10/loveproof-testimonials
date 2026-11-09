import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { describeFailure, getDashboardStats } from '../api'
import type { DashboardStats } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { LoadingIndicator } from '../components/LoadingIndicator'

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
      setError(describeFailure(cause, 'The dashboard could not be loaded.'))
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
        <p className="eyebrow">Moderation</p>
        <h1 className="page-title mt-1.5">Dashboard</h1>
        <p className="page-subtitle">
          Every testimonial submitted through the public form, by moderation
          state.
        </p>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}

      <LoadingIndicator
        label="Loading the dashboard…"
        loading={loading}
        hasContent={stats !== null}
      />

      {stats && (
        <div aria-busy={loading || undefined} className="flex flex-col gap-6">
          <dl className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <CountTile label="Pending" value={stats.pending} tone="amber" />
            <CountTile label="Approved" value={stats.approved} tone="emerald" />
            <CountTile label="Rejected" value={stats.rejected} tone="rose" />
            <CountTile label="Deleted" value={stats.deleted} tone="slate" />
          </dl>

          {stats.total === 0 && (
            <Card className="p-8 text-center" elevation="raised">
              <h2 className="text-lg font-semibold text-slate-900">
                No testimonials yet
              </h2>
              <p className="mx-auto mt-2 max-w-md text-sm leading-relaxed text-slate-600">
                Nothing has been submitted through the public form. The counts
                above fill in as submissions arrive.
              </p>
              <div className="mt-6">
                <Link to="/submit">
                  <Button>Open the public form</Button>
                </Link>
              </div>
            </Card>
          )}

          <div className="grid gap-4 lg:grid-cols-3">
            <Card className="p-5" elevation="raised">
              <p className="text-sm font-medium text-slate-600">
                Total testimonials
              </p>
              <p className="mt-2 bg-gradient-to-br from-brand-700 to-brand-500 bg-clip-text text-3xl font-bold tracking-tight text-transparent">
                {stats.total}
              </p>
              <p className="mt-1 text-sm text-slate-500">
                Excluding the {stats.deleted} deleted.
              </p>
            </Card>

            <Card className="p-5" elevation="raised">
              <p className="text-sm font-medium text-slate-600">
                Submitted in the last 7 days
              </p>
              <p className="mt-2 bg-gradient-to-br from-brand-700 to-brand-500 bg-clip-text text-3xl font-bold tracking-tight text-transparent">
                {stats.last7Days}
              </p>
            </Card>

            <Card className="p-5" elevation="raised">
              <p className="text-sm font-medium text-slate-600">
                Average approved rating
              </p>
              <p className="mt-2 bg-gradient-to-br from-brand-700 to-brand-500 bg-clip-text text-3xl font-bold tracking-tight text-transparent">
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
            <Link to="/admin/inbox">
              <Button>Open the moderation inbox</Button>
            </Link>
          </div>
        </div>
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
    <Card className="p-5" elevation="raised">
      <dt className="text-sm font-medium text-slate-600">{label}</dt>
      <dd
        className={`mt-2 inline-flex rounded-control px-2.5 py-1 text-3xl font-bold tracking-tight ring-1 ring-inset ${TONE_CLASSES[tone]}`}
      >
        {value}
      </dd>
    </Card>
  )
}
