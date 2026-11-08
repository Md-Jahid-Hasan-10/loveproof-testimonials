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
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-sm font-medium text-slate-600">{label}</p>
      <p className="mt-1 text-3xl font-bold tracking-tight text-slate-900">
        {value.toLocaleString('en-GB')}
      </p>
      <p className="mt-1 text-xs text-slate-500">{hint}</p>
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
          <h2 className="text-lg font-semibold text-slate-900">Your numbers</h2>
          <p className="mt-1 text-sm text-slate-600">
            Only your own Spaces and testimonials are counted.
          </p>
        </div>

        <div
          role="group"
          aria-label="Chart range"
          className="flex rounded-lg ring-1 ring-inset ring-slate-300"
        >
          {RANGES.map((range) => (
            <button
              key={range.days}
              type="button"
              aria-pressed={rangeDays === range.days}
              onClick={() => handleRangeChange(range.days)}
              className={`px-3 py-1.5 text-sm font-medium first:rounded-l-lg last:rounded-r-lg ${
                rangeDays === range.days
                  ? 'bg-brand-600 text-white'
                  : 'bg-white text-slate-700 hover:bg-slate-50'
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
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3">
          <p className="text-sm text-amber-900">
            The Premium plan unlocks more Spaces, more testimonials per Space
            and the carousel embed.
          </p>
          <Link
            to="/app/billing"
            className="mt-1 inline-block text-sm font-semibold text-amber-900 underline"
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

            <Card className="p-6">
              <h3 className="text-base font-semibold text-slate-900">
                Testimonials per day
              </h3>
              <p className="mt-1 text-sm text-slate-600">
                The last {dashboard.rangeDays} days, in UTC. A day with no
                submissions is shown as zero.
              </p>
              <div className="mt-4">
                <DashboardChart
                  daily={dashboard.daily ?? []}
                  rangeDays={dashboard.rangeDays}
                />
              </div>
            </Card>

            <Card className="p-6">
              <h3 className="text-base font-semibold text-slate-900">
                Recent submissions
              </h3>
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
