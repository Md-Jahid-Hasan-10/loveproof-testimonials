import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ApiError,
  curateSpaceTestimonial,
  deleteSpaceTestimonial,
  editSpaceTestimonial,
  getSpace,
  listSpaceTestimonials,
} from '../api'
import type { Space, SpaceTestimonial } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { ReadOnlyStars } from '../components/ReadOnlyStars'

/**
 * How many testimonials a page holds.
 */
const PAGE_SIZE = 20

/**
 * The filters the owner can narrow the inbox by.
 *
 * Each one is a tri-state: on, off, or "either". The server decides what a
 * filter means; this only says which one is asked for.
 */
interface Filters {
  favorite: boolean | null
  wallOfLove: boolean | null
  hidden: boolean | null
}

/**
 * One Space's testimonial inbox.
 *
 * The owner reads what people sent, and curates it: a favourite, an entry on
 * the Wall of Love, a hidden testimonial. Nothing is approved or rejected
 * here -- a submission is published the moment it arrives, and the owner
 * decides afterwards what to keep on show (PRD §4.7).
 *
 * A read-only Space can still be read. Every action is refused there, and
 * the buttons are disabled as well so the owner is not offered something the
 * server will reject.
 */
export default function SpaceInboxPage() {
  const { spaceId } = useParams<{ spaceId: string }>()

  const [space, setSpace] = useState<Space | null>(null)
  const [testimonials, setTestimonials] = useState<SpaceTestimonial[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [page, setPage] = useState(0)

  const [search, setSearch] = useState('')
  const [submittedSearch, setSubmittedSearch] = useState('')
  const [filters, setFilters] = useState<Filters>({
    favorite: null,
    wallOfLove: null,
    hidden: null,
  })

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  /** The testimonial being edited, and the text being typed into it. */
  const [editing, setEditing] = useState<{ id: string; content: string } | null>(
    null,
  )
  /** The testimonial waiting for a delete confirmation. */
  const [deleting, setDeleting] = useState<SpaceTestimonial | null>(null)

  const load = useCallback(async () => {
    if (!spaceId) {
      return
    }
    setLoading(true)
    try {
      const page_ = await listSpaceTestimonials(spaceId, {
        search: submittedSearch,
        favorite: filters.favorite ?? undefined,
        wallOfLove: filters.wallOfLove ?? undefined,
        hidden: filters.hidden ?? undefined,
        page,
        size: PAGE_SIZE,
      })
      setTestimonials(page_.content)
      setTotal(page_.total)
      setTotalPages(page_.totalPages)
      setError(null)
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'Your testimonials could not be loaded.',
      )
    } finally {
      setLoading(false)
    }
  }, [spaceId, submittedSearch, filters, page])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!spaceId) {
      return
    }
    getSpace(spaceId)
      .then(setSpace)
      .catch(() => {
        // The Space's own page reports a missing Space. Here it only means
        // the read-only banner and the disabled actions are unavailable.
        setSpace(null)
      })
  }, [spaceId])

  /**
   * Applies a curation change and refreshes the row that changed.
   *
   * The server answers with the updated testimonial, so the row is replaced
   * with the server's own version rather than a guess made in the browser.
   */
  const curate = async (
    testimonial: SpaceTestimonial,
    changes: { favorite?: boolean; wallOfLove?: boolean; visible?: boolean },
  ) => {
    setBusyId(testimonial.id)
    setError(null)
    try {
      const updated = await curateSpaceTestimonial(
        spaceId as string,
        testimonial.id,
        changes,
      )
      setTestimonials((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.code === 'CONFLICT'
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : 'That change could not be saved.',
      )
    } finally {
      setBusyId(null)
    }
  }

  const handleEditSave = async () => {
    if (!editing) {
      return
    }
    setBusyId(editing.id)
    setError(null)
    try {
      const updated = await editSpaceTestimonial(
        spaceId as string,
        editing.id,
        editing.content,
      )
      setTestimonials((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      )
      setEditing(null)
      setNotice('The testimonial was updated.')
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'The testimonial could not be updated.',
      )
    } finally {
      setBusyId(null)
    }
  }

  const handleDelete = async () => {
    if (!deleting) {
      return
    }
    setBusyId(deleting.id)
    setError(null)
    try {
      await deleteSpaceTestimonial(spaceId as string, deleting.id)
      setDeleting(null)
      setNotice('The testimonial was deleted.')
      await load()
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'The testimonial could not be deleted.',
      )
    } finally {
      setBusyId(null)
    }
  }

  const handleSearchSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setPage(0)
    setSubmittedSearch(search.trim())
  }

  const setFilter = (key: keyof Filters, value: boolean | null) => {
    setPage(0)
    setFilters((current) => ({ ...current, [key]: value }))
  }

  const editable = space?.editable ?? true

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="truncate text-2xl font-bold tracking-tight text-slate-900">
            Testimonials
          </h1>
          {space && (
            <p className="mt-1 text-sm text-slate-600">{space.title}</p>
          )}
        </div>

        {space && (
          <Link to={`/app/spaces/${space.id}`}>
            <Button variant="secondary">Space settings</Button>
          </Link>
        )}
      </div>

      {error && <ErrorBanner message={error} />}
      {notice && (
        <p
          role="status"
          className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900"
        >
          {notice}
        </p>
      )}

      {!editable && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
          <p className="text-sm font-semibold text-amber-900">
            This Space is read-only.
          </p>
          <p className="mt-1 text-sm text-amber-800">
            Your plan does not include this many Spaces, so its testimonials
            cannot be changed. You can still read them, and nothing has been
            deleted.
          </p>
        </div>
      )}

      <Card className="p-4">
        <form
          onSubmit={handleSearchSubmit}
          className="flex flex-wrap items-end gap-3"
        >
          <div className="min-w-48 flex-1">
            <label
              htmlFor="inbox-search"
              className="block text-sm font-medium text-slate-900"
            >
              Search
            </label>
            <input
              id="inbox-search"
              name="search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Name, email or text"
              className="mt-1 block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
            />
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
        </form>

        <div className="mt-4 flex flex-wrap items-center gap-4">
          {(
            [
              { key: 'favorite', label: 'Favourites' },
              { key: 'wallOfLove', label: 'Wall of Love' },
              { key: 'hidden', label: 'Hidden' },
            ] as { key: keyof Filters; label: string }[]
          ).map((filter) => (
            <label
              key={filter.key}
              className="flex items-center gap-2 text-sm text-slate-700"
            >
              <input
                type="checkbox"
                aria-label={filter.label}
                checked={filters[filter.key] === true}
                onChange={(event) =>
                  setFilter(
                    filter.key,
                    event.target.checked
                      ? true
                      : filters[filter.key] === true
                        ? null
                        : false,
                  )
                }
                className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              />
              {filter.label}
            </label>
          ))}

          {(filters.favorite !== null ||
            filters.wallOfLove !== null ||
            filters.hidden !== null ||
            submittedSearch !== '') && (
            <button
              type="button"
              onClick={() => {
                setFilters({ favorite: null, wallOfLove: null, hidden: null })
                setSearch('')
                setSubmittedSearch('')
                setPage(0)
              }}
              className="text-sm font-medium text-brand-700 underline"
            >
              Clear filters
            </button>
          )}
        </div>
      </Card>

      {loading && (
        <p role="status" aria-live="polite" className="text-sm text-slate-600">
          Loading your testimonials…
        </p>
      )}

      {!loading && testimonials.length === 0 && (
        <p className="text-sm text-slate-600">
          {submittedSearch === '' &&
          filters.favorite === null &&
          filters.wallOfLove === null &&
          filters.hidden === null
            ? 'No testimonials yet. Share your public link and they will arrive here.'
            : 'No testimonials match these filters.'}
        </p>
      )}

      <ul className="space-y-3">
        {testimonials.map((testimonial) => (
          <li key={testimonial.id}>
            <Card className="p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold text-slate-900">
                      {testimonial.name}
                    </p>
                    {testimonial.rating !== null && (
                      <ReadOnlyStars rating={testimonial.rating} />
                    )}
                    {testimonial.favorite && (
                      <span className="rounded-md bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-900">
                        Favourite
                      </span>
                    )}
                    {testimonial.wallOfLove && (
                      <span className="rounded-md bg-brand-100 px-2 py-0.5 text-xs font-semibold text-brand-800">
                        Wall of Love
                      </span>
                    )}
                    {!testimonial.visible && (
                      <span className="rounded-md bg-slate-200 px-2 py-0.5 text-xs font-semibold text-slate-700">
                        Hidden
                      </span>
                    )}
                  </div>

                  <p className="mt-0.5 truncate text-sm text-slate-500">
                    {testimonial.email}
                  </p>

                  {editing?.id === testimonial.id ? (
                    <div className="mt-3 space-y-2">
                      <label
                        htmlFor={`edit-${testimonial.id}`}
                        className="block text-sm font-medium text-slate-900"
                      >
                        Testimonial text
                      </label>
                      <textarea
                        id={`edit-${testimonial.id}`}
                        rows={4}
                        value={editing.content}
                        onChange={(event) =>
                          setEditing({
                            id: editing.id,
                            content: event.target.value,
                          })
                        }
                        className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                      />
                      <div className="flex gap-2">
                        <Button
                          onClick={() => void handleEditSave()}
                          loading={busyId === testimonial.id}
                        >
                          Save
                        </Button>
                        <Button
                          variant="secondary"
                          onClick={() => setEditing(null)}
                        >
                          Cancel
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">
                      {testimonial.content}
                    </p>
                  )}

                  <p className="mt-2 text-xs text-slate-500">
                    Submitted on{' '}
                    {new Date(testimonial.submittedAt).toLocaleDateString()}
                  </p>
                </div>
              </div>

              <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3">
                <Button
                  variant="secondary"
                  disabled={!editable}
                  loading={busyId === testimonial.id}
                  onClick={() =>
                    void curate(testimonial, { favorite: !testimonial.favorite })
                  }
                  aria-label={
                    testimonial.favorite
                      ? `Remove ${testimonial.name} from favourites`
                      : `Add ${testimonial.name} to favourites`
                  }
                >
                  {testimonial.favorite ? 'Unfavourite' : 'Favourite'}
                </Button>

                <Button
                  variant="secondary"
                  disabled={!editable}
                  loading={busyId === testimonial.id}
                  onClick={() =>
                    void curate(testimonial, {
                      wallOfLove: !testimonial.wallOfLove,
                    })
                  }
                  aria-label={
                    testimonial.wallOfLove
                      ? `Remove ${testimonial.name} from the Wall of Love`
                      : `Add ${testimonial.name} to the Wall of Love`
                  }
                >
                  {testimonial.wallOfLove ? 'Remove from Wall of Love' : 'Add to Wall of Love'}
                </Button>

                <Button
                  variant="secondary"
                  disabled={!editable}
                  loading={busyId === testimonial.id}
                  onClick={() =>
                    void curate(testimonial, { visible: !testimonial.visible })
                  }
                  aria-label={
                    testimonial.visible
                      ? `Hide ${testimonial.name}`
                      : `Show ${testimonial.name}`
                  }
                >
                  {testimonial.visible ? 'Hide' : 'Show'}
                </Button>

                <Button
                  variant="secondary"
                  disabled={!editable}
                  onClick={() =>
                    setEditing({
                      id: testimonial.id,
                      content: testimonial.content,
                    })
                  }
                  aria-label={`Edit ${testimonial.name}`}
                >
                  Edit
                </Button>

                <Button
                  variant="secondary"
                  disabled={!editable}
                  onClick={() => setDeleting(testimonial)}
                  aria-label={`Delete ${testimonial.name}`}
                >
                  Delete
                </Button>
              </div>
            </Card>
          </li>
        ))}
      </ul>

      {deleting && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-delete-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4"
        >
          <Card className="w-full max-w-md p-6">
            <h2
              id="confirm-delete-title"
              className="text-base font-semibold text-slate-900"
            >
              Delete this testimonial?
            </h2>
            <p className="mt-2 text-sm text-slate-600">
              The testimonial from {deleting.name} will be removed for good.
              This cannot be undone.
            </p>
            <div className="mt-4 flex justify-end gap-2">
              <Button
                variant="secondary"
                onClick={() => setDeleting(null)}
              >
                Cancel
              </Button>
              <Button
                loading={busyId === deleting.id}
                onClick={() => void handleDelete()}
              >
                Delete
              </Button>
            </div>
          </Card>
        </div>
      )}

      {totalPages > 1 && (
        <nav
          aria-label="Pagination"
          className="flex items-center justify-between gap-3"
        >
          <Button
            variant="secondary"
            disabled={page === 0}
            onClick={() => setPage((current) => Math.max(current - 1, 0))}
          >
            Previous
          </Button>
          <p className="text-sm text-slate-600">
            Page {page + 1} of {totalPages} &middot; {total} testimonial
            {total === 1 ? '' : 's'}
          </p>
          <Button
            variant="secondary"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            Next
          </Button>
        </nav>
      )}
    </div>
  )
}
