import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { describeFailure, getDashboardAnalytics } from '../api'
import type { DashboardAnalytics } from '../api'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { DashboardChart } from './DashboardChart'

/**
 * The dashboard's analytics: the totals, the daily chart and the recent
 * submissions (PRD §4.6).
 *
 * <p>Two rules shape what is on screen:
 *
 * <ul>
 *   <li>**Only the account's own data.** The numbers come from the server,
 *       scoped to the signed-in account's Spaces. The interface never
 *       filters them itself, so it cannot show a number the server did not
 *       agree to.</li>
 *   <li>**Free accounts are told about Premium.** A Free account sees the
 *       upgrade notice; a Premium account does not.</li>
 * </ul>
 */

/** The ranges the chart can cover. */
const RANGES = [
  { days: 7, label: '7 days' },
  { days: 30, label: '30 days' },
  { days: 90, label: '90 days' },
  { days: 365, label: '12 months' },
]

/**
 * Formats a submission's timestamp for the recent list.
 *
 * @param submittedAt the ISO timestamp
 * @return a readable form
 */
function submittedAt(submittedAt: string): string {
  const parsed = new Date(submittedAt)
  if (Number.isNaN(parsed.getTime())) {
    return submittedAt
  }
  return parsed.toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/**
 * One of the three headline numbers.
 */
function Stat({
  label,
  value,
  hint,
}: {
  label: string
  value: number
  hint: string
}) {
  return (
    <div className="surface-raised p-5 transition-shadow duration-200 hover:shadow-lifted">
      <p className="text-sm font-medium text-slate-600">{label}</p>
      <p className="mt-1.5 bg-gradient-to-br from-brand-700 to-brand-500 bg-clip-text text-3xl font-bold tracking-tight text-transparent">
        {value.toLocaleString('en-GB')}
      </p>
      <p className="mt-1.5 text-xs text-slate-500">{hint}</p>
    </div>
  )
}

export function DashboardAnalyticsPanel() {
  const [dashboard, setDashboard] = useState<DashboardAnalytics | null>(null)
  const [rangeDays, setRangeDays] = useState(30)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (days: number) => {
    setLoading(true)
    try {
      const loaded = await getDashboardAnalytics(days)
      setDashboard(loaded)
      setError(null)
    } catch (cause) {
      setError(
        describeFailure(cause, 'Your dashboard numbers could not be loaded.'),
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(rangeDays)
  }, [load, rangeDays])

  const handleRangeChange = (days: number) => {
    setRangeDays(days)
  }

  const isFree = dashboard?.plan !== 'PREMIUM'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="section-title">Your numbers</h2>
          <p className="section-subtitle">
            Only your own Spaces and testimonials are counted.
          </p>
        </div>

        <div
          role="group"
          aria-label="Chart range"
          className="inline-flex rounded-control bg-slate-100 p-1"
        >
          {RANGES.map((range) => (
            <button
              key={range.days}
              type="button"
              aria-pressed={rangeDays === range.days}
              onClick={() => handleRangeChange(range.days)}
              className={`rounded-[0.4rem] px-3 py-1.5 text-sm font-medium transition ${
                rangeDays === range.days
                  ? 'bg-white text-brand-700 shadow-sm ring-1 ring-inset ring-slate-200/70'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {range.label}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <ErrorBanner message={error} onRetry={() => void load(rangeDays)} />
      )}

      {isFree && !loading && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-amber-200/80 bg-gradient-to-r from-amber-50 to-orange-50/60 px-4 py-3">
          <p className="text-sm text-amber-900">
            The Premium plan unlocks more Spaces, more testimonials per Space
            and the carousel embed.
          </p>
          <Link
            to="/app/billing"
            className="text-sm font-semibold text-amber-900 underline underline-offset-2 hover:text-amber-950"
          >
            See what Premium offers
          </Link>
        </div>
      )}

      {loading && !dashboard ? (
        <p role="status" aria-live="polite" className="text-sm text-slate-600">
          Loading your numbers…
        </p>
      ) : (
        dashboard && (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <Stat
                label="Spaces"
                value={dashboard.totalSpaces}
                hint={`of ${dashboard.spacesLimit} on your plan`}
              />
              <Stat
                label="Testimonials collected"
                value={dashboard.totalTestimonials}
                hint="across your Spaces"
              />
              <Stat
                label="Unique submitters"
                value={dashboard.uniqueSubmitters}
                hint="distinct people who wrote one"
              />
            </div>

            <Card className="p-6" elevation="raised">
              <h3 className="section-title">Testimonials per day</h3>
              <p className="section-subtitle">
                The last {dashboard.rangeDays} days, in UTC. A day with no
                submissions is shown as zero.
              </p>
              <div className="mt-5">
                <DashboardChart
                  daily={dashboard.daily ?? []}
                  rangeDays={dashboard.rangeDays}
                />
              </div>
            </Card>

            <Card className="p-6" elevation="raised">
              <h3 className="section-title">Recent submissions</h3>
              {(dashboard.recent ?? []).length === 0 ? (
                <p className="mt-2 text-sm text-slate-600">
                  Nothing has been submitted yet. Share a Space&rsquo;s public
                  link to start collecting.
                </p>
              ) : (
                <ul className="mt-3 divide-y divide-slate-100">
                  {(dashboard.recent ?? []).map((submission) => (
                    <li key={submission.id} className="py-3 first:pt-0 last:pb-0">
                      <div className="flex flex-wrap items-baseline justify-between gap-2">
                        <p className="text-sm font-medium text-slate-900">
                          {submission.name}
                          <span className="ml-2 text-xs font-normal text-slate-500">
                            in {submission.spaceTitle}
                          </span>
                        </p>
                        <p className="text-xs text-slate-500">
                          {submittedAt(submission.submittedAt)}
                        </p>
                      </div>
                      <p className="mt-1 line-clamp-2 text-sm text-slate-600">
                        {submission.content}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </>
        )
      )}
    </div>
  )
}
