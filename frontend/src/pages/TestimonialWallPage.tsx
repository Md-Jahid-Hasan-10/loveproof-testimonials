import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { describeFailure, getPublicStats, listTestimonials } from '../api'
import type { PublicStats, Testimonial } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { LoadingIndicator } from '../components/LoadingIndicator'
import { ReadOnlyStars } from '../components/ReadOnlyStars'

const PAGE_SIZE = 12

/** The orderings the wall offers. */
const SORT_OPTIONS = [
  { value: 'submittedAt', label: 'Newest first' },
  { value: 'rating', label: 'Highest rated' },
] as const

type SortOption = (typeof SORT_OPTIONS)[number]['value']

/**
 * The public wall.
 *
 * Only approved, non-deleted testimonials with a recorded consent ever reach
 * this page — the backend enforces that, so the browser never receives a
 * pending, rejected or deleted testimonial in the first place (FR-5.1,
 * FR-5.7).
 *
 * The page holds no testimonial content of its own: everything it renders came
 * from `GET /api/testimonials`, filtered by the rating and sorted by the
 * ordering the visitor chose.
 */
export default function TestimonialWallPage() {
  const [rating, setRating] = useState<number | null>(null)
  const [sort, setSort] = useState<SortOption>('submittedAt')
  const [page, setPage] = useState(0)

  const [testimonials, setTestimonials] = useState<Testimonial[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [stats, setStats] = useState<PublicStats | null>(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listTestimonials({
        rating: rating ?? undefined,
        sort,
        page,
        size: PAGE_SIZE,
      })
      setTestimonials(result.content)
      setTotal(result.total)
      setTotalPages(result.totalPages)
    } catch (cause) {
      setError(describeFailure(cause, 'The testimonials could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [rating, sort, page])

  useEffect(() => {
    void load()
  }, [load])

  // The headline count is the whole wall, not the filtered page, so it is
  // fetched separately and never changes with the filter.
  useEffect(() => {
    let cancelled = false
    void getPublicStats()
      .then((result) => {
        if (!cancelled) setStats(result)
      })
      .catch(() => {
        // The count is a nicety. If it cannot be read, the wall still works.
        if (!cancelled) setStats(null)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const handleRatingChange = (next: number | null) => {
    setRating(next)
    setPage(0)
  }

  const handleSortChange = (next: SortOption) => {
    setSort(next)
    setPage(0)
  }

  const filtered = rating !== null

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          What our customers say
        </h1>
        {stats && (
          <p className="mt-1 text-sm text-slate-600">
            {stats.count} published{' '}
            {stats.count === 1 ? 'testimonial' : 'testimonials'}
            {stats.averageRating !== null && (
              <>
                {' · '}
                {stats.averageRating.toFixed(1)} out of 5 on average
              </>
            )}
          </p>
        )}
      </div>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <fieldset className="flex flex-col gap-1.5">
          <legend className="text-sm font-medium text-slate-900">
            Filter by rating
          </legend>
          <div className="flex flex-wrap gap-1">
            <FilterButton
              selected={rating === null}
              onClick={() => handleRatingChange(null)}
            >
              All ratings
            </FilterButton>
            {[5, 4, 3, 2, 1].map((value) => (
              <FilterButton
                key={value}
                selected={rating === value}
                onClick={() => handleRatingChange(value)}
              >
                {value} star{value === 1 ? '' : 's'}
              </FilterButton>
            ))}
          </div>
        </fieldset>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="wall-sort" className="text-sm font-medium text-slate-900">
            Sort by
          </label>
          <select
            id="wall-sort"
            value={sort}
            onChange={(event) => handleSortChange(event.target.value as SortOption)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-200"
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}

      <LoadingIndicator
        label="Loading testimonials…"
        loading={loading}
        hasContent={testimonials.length > 0}
      />

      {!loading && !error && testimonials.length === 0 && (
        <Card className="p-10 text-center">
          <h2 className="text-lg font-semibold text-slate-900">
            {filtered ? 'No testimonials with that rating' : 'No testimonials yet'}
          </h2>
          <p className="mx-auto mt-2 max-w-md text-sm text-slate-600">
            {filtered
              ? 'Try a different rating, or clear the filter to see every published testimonial.'
              : 'Once a testimonial is approved by a moderator it appears here. Be the first to share yours.'}
          </p>
          <div className="mt-6">
            <Link
              to="/submit"
              className="inline-flex items-center rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
            >
              Share your story
            </Link>
          </div>
        </Card>
      )}

      {testimonials.length > 0 && (
        <p aria-live="polite" className="text-sm text-slate-600">
          Showing {testimonials.length} of {total}{' '}
          {total === 1 ? 'testimonial' : 'testimonials'}
          {filtered ? ' with that rating' : ''}.
        </p>
      )}

      <ul
        className="flex flex-col gap-4"
        aria-busy={loading || undefined}
        aria-label="Published testimonials"
      >
        {testimonials.map((testimonial) => (
          <li key={testimonial.id}>
            <TestimonialCard testimonial={testimonial} />
          </li>
        ))}
      </ul>

      {totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between">
          <Button
            variant="secondary"
            disabled={page === 0 || loading}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            Previous
          </Button>
          <p className="text-sm text-slate-600">
            Page {page + 1} of {totalPages}
          </p>
          <Button
            variant="secondary"
            disabled={page >= totalPages - 1 || loading}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </Button>
        </nav>
      )}
    </div>
  )
}

/**
 * One published testimonial.
 */
function TestimonialCard({ testimonial }: { testimonial: Testimonial }) {
  return (
    <Card className="p-5">
      <blockquote className="flex flex-col gap-3">
        <p className="whitespace-pre-line break-words text-sm leading-relaxed text-slate-700">
          {testimonial.content}
        </p>
      </blockquote>

      {/*
        The attribution is a plain container, not a `footer` element. A
        `footer` outside a sectioning element is a `contentinfo` landmark, so
        one per card would give the page several indistinguishable landmarks.
      */}
      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
        <div className="flex min-w-0 flex-col gap-1">
          <cite className="not-italic font-semibold break-words text-slate-900">
            {testimonial.name}
          </cite>
          {(testimonial.role || testimonial.company) && (
            <p className="break-words text-sm text-slate-500">
              {[testimonial.role, testimonial.company]
                .filter(Boolean)
                .join(' · ')}
            </p>
          )}
        </div>
        <ReadOnlyStars rating={testimonial.rating} />
      </div>
    </Card>
  )
}

/**
 * One button in the rating filter group.
 */
function FilterButton({
  selected,
  onClick,
  children,
}: {
  selected: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={`rounded-lg px-3 py-2 text-sm font-medium ${
        selected
          ? 'bg-brand-600 text-white'
          : 'bg-white text-slate-700 ring-1 ring-inset ring-slate-300 hover:bg-slate-50'
      }`}
    >
      {children}
    </button>
  )
}
