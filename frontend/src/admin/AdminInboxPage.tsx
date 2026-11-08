import { useCallback, useEffect, useState } from 'react'
import {
  approveTestimonial,
  deleteTestimonial,
  describeFailure,
  listInbox,
  rejectTestimonial,
} from '../api'
import type { InboxFilter, Testimonial } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { LoadingIndicator } from '../components/LoadingIndicator'

const PAGE_SIZE = 20

/**
 * The moderation inbox.
 *
 * The pending queue is the work list. Approved and rejected testimonials stay
 * visible so a moderator can see what was already decided, and the deleted
 * ones are a separate view because a deleted testimonial keeps the status it
 * had when it was deleted.
 */
export default function AdminInboxPage() {
  const [filter, setFilter] = useState<InboxFilter>('PENDING')
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const [testimonials, setTestimonials] = useState<Testimonial[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await listInbox({
        status: filter === 'ALL' ? undefined : filter,
        q: search,
        page,
        size: PAGE_SIZE,
      })
      setTestimonials(result.content)
      setTotal(result.total)
      setTotalPages(result.totalPages)
    } catch (cause) {
      setError(describeFailure(cause, 'The inbox could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [filter, search, page])

  useEffect(() => {
    void load()
  }, [load])

  /**
   * Runs a moderation action and refreshes the list.
   *
   * The list is reloaded rather than patched in place: the counts, the filters
   * and the rows all depend on the server's view, and only the server knows it.
   */
  const runAction = async (
    id: string,
    action: () => Promise<unknown>,
    failureMessage: string,
  ) => {
    setBusyId(id)
    setActionError(null)
    try {
      await action()
      await load()
    } catch (cause) {
      setActionError(describeFailure(cause, failureMessage))
    } finally {
      setBusyId(null)
    }
  }

  const handleApprove = (testimonial: Testimonial) =>
    void runAction(
      testimonial.id,
      () => approveTestimonial(testimonial.id),
      'The testimonial could not be approved.',
    )

  const handleReject = (testimonial: Testimonial, reason: string) =>
    void runAction(
      testimonial.id,
      () => rejectTestimonial(testimonial.id, reason),
      'The testimonial could not be rejected.',
    )

  const handleDelete = (testimonial: Testimonial) =>
    void runAction(
      testimonial.id,
      () => deleteTestimonial(testimonial.id),
      'The testimonial could not be deleted.',
    )

  const handleFilterChange = (next: InboxFilter) => {
    setFilter(next)
    setPage(0)
  }

  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSearch(query.trim())
    setPage(0)
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Moderation inbox
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          {total} {total === 1 ? 'testimonial' : 'testimonials'} match the
          current filter.
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div role="tablist" aria-label="Filter by status" className="flex flex-wrap gap-1">
          {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as InboxFilter[]).map(
            (option) => (
              <button
                key={option}
                type="button"
                role="tab"
                aria-selected={filter === option}
                aria-controls="inbox-results"
                // The roving tabindex: only the selected tab is in the tab
                // order, so the arrow keys are what move between tabs. A
                // screen reader announces the whole group as one set.
                tabIndex={filter === option ? 0 : -1}
                onClick={() => handleFilterChange(option)}
                className={`rounded-lg px-3 py-2 text-sm font-medium ${
                  filter === option
                    ? 'bg-brand-600 text-white'
                    : 'bg-white text-slate-700 ring-1 ring-inset ring-slate-300 hover:bg-slate-50'
                }`}
              >
                {option === 'ALL' ? 'All' : option.charAt(0) + option.slice(1).toLowerCase()}
              </button>
            ),
          )}
        </div>

        <form onSubmit={handleSearchSubmit} className="flex flex-wrap items-end gap-2">
          <div className="flex min-w-0 flex-1 flex-col gap-1.5 sm:flex-none">
            <label htmlFor="inbox-search" className="text-sm font-medium text-slate-900">
              Search
            </label>
            <input
              id="inbox-search"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Name, company or text"
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-200"
            />
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => void load()} />}
      {actionError && (
        <ErrorBanner
          title="The action failed"
          message={actionError}
          onDismiss={() => setActionError(null)}
        />
      )}

      <LoadingIndicator
        label="Loading the inbox…"
        loading={loading}
        hasContent={testimonials.length > 0}
      />

      {/*
        The empty state is only shown when the list really is empty. When the
        load failed, the error banner above already says so, and "Nothing
        matches this filter" on top of it would contradict it.
      */}
      {!loading && !error && testimonials.length === 0 && (
        <Card className="p-8 text-center">
          <p className="text-sm text-slate-600">
            Nothing matches this filter.
          </p>
        </Card>
      )}

      {/*
        The tab panel is a plain container: the `tabpanel` role is not allowed
        on a list element, so the list sits inside it rather than carrying the
        role itself.
      */}
      <div
        id="inbox-results"
        role="tabpanel"
        aria-busy={loading || undefined}
        aria-label="Testimonials awaiting moderation"
      >
        <ul className="flex flex-col gap-4">
          {testimonials.map((testimonial) => (
            <li key={testimonial.id}>
              <TestimonialRow
                testimonial={testimonial}
                busy={busyId === testimonial.id}
                onApprove={() => handleApprove(testimonial)}
                onReject={(reason) => handleReject(testimonial, reason)}
                onDelete={() => handleDelete(testimonial)}
              />
            </li>
          ))}
        </ul>
      </div>

      {totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between">
          <Button
            variant="secondary"
            disabled={page === 0}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
          >
            Previous
          </Button>
          <p className="text-sm text-slate-600">
            Page {page + 1} of {totalPages}
          </p>
          <Button
            variant="secondary"
            disabled={page >= totalPages - 1}
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
 * One testimonial in the inbox, with the actions that apply to it.
 */
function TestimonialRow({
  testimonial,
  busy,
  onApprove,
  onReject,
  onDelete,
}: {
  testimonial: Testimonial
  busy: boolean
  onApprove: () => void
  onReject: (reason: string) => void
  onDelete: () => void
}) {
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)

  const submitRejection = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const trimmed = reason.trim()
    if (trimmed.length === 0) {
      setReasonError('A rejection reason is required.')
      return
    }
    setReasonError(null)
    onReject(trimmed)
    setRejecting(false)
    setReason('')
  }

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="break-words font-semibold text-slate-900">{testimonial.name}</p>
            <StatusBadge status={testimonial.status} />
            <span
              className="text-sm text-amber-600"
              aria-label={`${testimonial.rating} out of 5 stars`}
            >
              {'★'.repeat(testimonial.rating)}
              <span className="sr-only">
                {testimonial.rating} out of 5 stars
              </span>
            </span>
          </div>

          {(testimonial.role || testimonial.company) && (
            <p className="mt-1 break-words text-sm text-slate-500">
              {[testimonial.role, testimonial.company]
                .filter(Boolean)
                .join(' · ')}
            </p>
          )}

          <p className="mt-3 whitespace-pre-line break-words text-sm leading-relaxed text-slate-700">
            {testimonial.content}
          </p>

          {/*
            The timestamp is `text-slate-500`, not a lighter grey: slate-400
            on white is only 3:1, which is below the 4.5:1 that axe requires
            for body text.
          */}
          <p className="mt-3 text-xs text-slate-500">
            Submitted{' '}
            {new Date(testimonial.submittedAt).toLocaleString()}
          </p>
        </div>

        <div className="flex shrink-0 flex-wrap gap-2">
          {testimonial.status === 'PENDING' && (
            <>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={onApprove}
                className="text-emerald-700"
              >
                Approve
              </Button>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() => setRejecting((current) => !current)}
                className="text-rose-700"
              >
                Reject
              </Button>
            </>
          )}

          {testimonial.status === 'APPROVED' && (
            <Button
              variant="secondary"
              disabled={busy}
              onClick={onDelete}
              className="text-slate-700"
            >
              Delete
            </Button>
          )}

          {testimonial.status === 'REJECTED' && (
            <>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={onDelete}
                className="text-slate-700"
              >
                Delete
              </Button>
              <Button
                variant="secondary"
                disabled={busy}
                onClick={() => setRejecting((current) => !current)}
                className="text-slate-700"
              >
                Change reason
              </Button>
            </>
          )}
        </div>
      </div>

      {rejecting && (
        <form
          onSubmit={submitRejection}
          className="mt-4 flex flex-col gap-2 rounded-lg border border-slate-200 bg-slate-50 p-4"
        >
          <label
            htmlFor={`reason-${testimonial.id}`}
            className="text-sm font-medium text-slate-900"
          >
            Reason for rejecting
          </label>
          <input
            id={`reason-${testimonial.id}`}
            type="text"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            aria-invalid={reasonError ? true : undefined}
            aria-describedby={reasonError ? `reason-error-${testimonial.id}` : undefined}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-200"
          />
          {reasonError && (
            <p id={`reason-error-${testimonial.id}`} className="text-sm font-medium text-rose-600">
              {reasonError}
            </p>
          )}
          <div className="flex gap-2">
            <Button type="submit" variant="secondary" className="text-rose-700">
              Save reason
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setRejecting(false)
                setReasonError(null)
              }}
            >
              Cancel
            </Button>
          </div>
        </form>
      )}
    </Card>
  )
}

/** The moderation status of a testimonial. */
function StatusBadge({ status }: { status: Testimonial['status'] }) {
  const classes: Record<Testimonial['status'], string> = {
    PENDING: 'bg-amber-50 text-amber-700 ring-amber-200',
    APPROVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
    REJECTED: 'bg-rose-50 text-rose-700 ring-rose-200',
  }

  return (
    <span
      className={`rounded-md px-2 py-0.5 text-xs font-semibold ring-1 ring-inset ${classes[status]}`}
    >
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  )
}
