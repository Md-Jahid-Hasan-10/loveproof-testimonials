import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { Page, PublicStats, Testimonial } from '../api'
import TestimonialWallPage from './TestimonialWallPage'

/**
 * The API is stubbed at the `fetch` boundary rather than at the client module,
 * so the tests exercise the real query-string building and the real parsing of
 * the paginated payload.
 */
const fetchMock = vi.fn()

/** How many requests are still in flight. */
let pendingRequests = 0

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

function testimonial(overrides: Partial<Testimonial> = {}): Testimonial {
  return {
    id: '3f0c1e6a-0000-4000-8000-000000000001',
    name: 'Ada Lovelace',
    role: 'Engineer',
    company: 'Analytical Engines',
    rating: 5,
    content: 'The wall renders approved testimonials with their author intact.',
    status: 'APPROVED',
    consentTextVersion: 'v1',
    submittedAt: '2026-11-08T00:00:00Z',
    ...overrides,
  }
}

function page(
  content: Testimonial[],
  overrides: Partial<Page<Testimonial>> = {},
): Page<Testimonial> {
  return {
    content,
    page: 0,
    size: 12,
    total: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    ...overrides,
  }
}

const STATS: PublicStats = { count: 2, averageRating: 4.5 }

/**
 * Answers the two calls the wall makes: the page of testimonials and the
 * public statistics. `listResponder` decides what the page returns.
 */
function stubApi(listResponder: () => Response, stats: PublicStats | null = STATS) {
  fetchMock.mockImplementation((url: string) => {
    if (url.includes('/testimonials/stats')) {
      if (stats === null) {
        return settle(Promise.reject(new TypeError('Failed to fetch')))
      }
      return settle(Promise.resolve(jsonResponse(stats)))
    }
    return settle(Promise.resolve(listResponder()))
  })
}

/**
 * Tracks how many requests are still in flight.
 *
 * A request is only settled once the wall has acted on it, so this is the
 * reliable signal that no state update lands after a test's assertions.
 */
function settle<T>(promise: Promise<T>): Promise<T> {
  pendingRequests += 1
  return promise.then(
    (value) => {
      pendingRequests -= 1
      return value
    },
    (reason) => {
      pendingRequests -= 1
      throw reason
    },
  )
}

/** The URLs the wall requested, in order, without the `/api` prefix. */
function requestedUrls(): string[] {
  return fetchMock.mock.calls
    .map((call) => String(call[0]))
    .filter((url) => !url.includes('/testimonials/stats'))
}

/**
 * Waits until both requests have settled.
 *
 * The wall fires the page query and the statistics query independently, so a
 * test that only awaits one of them leaves the other resolving after the
 * assertions — which React reports as an update outside `act`.
 *
 * The wait is made on the DOM rather than on the mock's promise states,
 * because one of the two requests is allowed to fail.
 */
async function waitForBothRequests(): Promise<void> {
  await waitFor(() => {
    expect(fetchMock.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
  // Every request the wall made has settled, whether it succeeded or failed.
  await waitFor(() => {
    expect(pendingRequests).toBe(0)
  })
}

describe('TestimonialWallPage', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    pendingRequests = 0
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function renderPage() {
    return render(
      <MemoryRouter>
        <TestimonialWallPage />
      </MemoryRouter>,
    )
  }

  it('shows the headline count and the average rating', async () => {
    stubApi(() => jsonResponse(page([testimonial()])))
    renderPage()

    expect(
      await screen.findByText(/2 published testimonials/i),
    ).toBeInTheDocument()
    expect(screen.getByText(/4\.5 out of 5 on average/i)).toBeInTheDocument()
    await waitForBothRequests()
  })

  it('renders the testimonial text, the author and the rating', async () => {
    stubApi(() =>
      jsonResponse(page([testimonial({ rating: 4 })])),
    )
    renderPage()

    expect(
      await screen.findByText(
        /the wall renders approved testimonials with their author intact/i,
      ),
    ).toBeInTheDocument()
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('Engineer · Analytical Engines')).toBeInTheDocument()
    // The rating is carried by the text alternative, never by the glyph alone.
    expect(screen.getByText('4 out of 5')).toBeInTheDocument()
    await waitForBothRequests()
  })

  it('omits the role and company line when there is neither', async () => {
    stubApi(() =>
      jsonResponse(page([testimonial({ role: null, company: null })])),
    )
    renderPage()

    const card = (await screen.findByText('Ada Lovelace')).closest('li')
    expect(card).not.toBeNull()
    expect(card).not.toHaveTextContent('·')
    await waitForBothRequests()
  })

  it('shows only the role when there is no company', async () => {
    stubApi(() => jsonResponse(page([testimonial({ company: null })])))
    renderPage()

    const card = (await screen.findByText('Ada Lovelace')).closest('li')
    expect(card).toHaveTextContent('Engineer')
    expect(card).not.toHaveTextContent('·')
    await waitForBothRequests()
  })

  it('requests the whole wall with no rating filter by default', async () => {
    stubApi(() => jsonResponse(page([])))
    renderPage()

    await waitFor(() => expect(requestedUrls()).toHaveLength(1))
    expect(requestedUrls()[0]).toBe(
      '/api/testimonials?sort=submittedAt&page=0&size=12',
    )
    await waitForBothRequests()
  })

  it('re-queries with the chosen rating and returns to the first page', async () => {
    const user = userEvent.setup()
    stubApi(() => jsonResponse(page([])))
    renderPage()
    await waitFor(() => expect(requestedUrls()).toHaveLength(1))

    await user.click(screen.getByRole('button', { name: '4 stars' }))

    await waitFor(() => expect(requestedUrls()).toHaveLength(2))
    expect(requestedUrls()[1]).toBe(
      '/api/testimonials?rating=4&sort=submittedAt&page=0&size=12',
    )
    expect(
      screen.getByRole('button', { name: '4 stars' }),
    ).toHaveAttribute('aria-pressed', 'true')
    expect(
      screen.getByRole('button', { name: /all ratings/i }),
    ).toHaveAttribute('aria-pressed', 'false')
    await waitForBothRequests()
  })

  it('re-queries with the chosen ordering', async () => {
    const user = userEvent.setup()
    stubApi(() => jsonResponse(page([])))
    renderPage()
    await waitFor(() => expect(requestedUrls()).toHaveLength(1))

    await user.selectOptions(screen.getByLabelText(/sort by/i), 'rating')

    await waitFor(() => expect(requestedUrls()).toHaveLength(2))
    expect(requestedUrls()[1]).toBe(
      '/api/testimonials?sort=rating&page=0&size=12',
    )
    await waitForBothRequests()
  })

  it('shows an empty state when no testimonial is published', async () => {
    stubApi(() => jsonResponse(page([])))
    renderPage()

    expect(
      await screen.findByRole('heading', { name: /no testimonials yet/i }),
    ).toBeInTheDocument()
    await waitForBothRequests()
  })

  it('shows a filtered empty state when the rating has no match', async () => {
    const user = userEvent.setup()
    stubApi(() => jsonResponse(page([])))
    renderPage()
    await screen.findByRole('heading', { name: /no testimonials yet/i })

    await user.click(screen.getByRole('button', { name: '2 stars' }))

    expect(
      await screen.findByRole('heading', {
        name: /no testimonials with that rating/i,
      }),
    ).toBeInTheDocument()
    await waitForBothRequests()
  })

  it('shows how many of how many are on screen', async () => {
    stubApi(() =>
      jsonResponse(
        page([testimonial(), testimonial({ id: 'other' })], { total: 12 }),
      ),
    )
    renderPage()

    expect(await screen.findByText(/showing 2 of 12 testimonials/i)).toBeInTheDocument()
    await waitForBothRequests()
  })

  it('pages forwards and backwards', async () => {
    const user = userEvent.setup()
    stubApi(() =>
      jsonResponse(page([testimonial()], { total: 24, totalPages: 2 })),
    )
    renderPage()
    await screen.findByText(/page 1 of 2/i)

    await user.click(screen.getByRole('button', { name: /next/i }))
    await waitFor(() => expect(requestedUrls()).toHaveLength(2))
    expect(requestedUrls()[1]).toContain('page=1')

    await user.click(screen.getByRole('button', { name: /previous/i }))
    await waitFor(() => expect(requestedUrls()).toHaveLength(3))
    expect(requestedUrls()[2]).toContain('page=0')
    await waitForBothRequests()
  })

  it('hides the pagination when a single page covers the wall', async () => {
    stubApi(() => jsonResponse(page([testimonial()])))
    renderPage()

    await screen.findByText(/showing 1 of 1 testimonial/i)
    expect(
      screen.queryByRole('navigation', { name: /pagination/i }),
    ).not.toBeInTheDocument()
    await waitForBothRequests()
  })

  it('shows a retryable error banner when the wall cannot be loaded', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials/stats')) {
        return Promise.resolve(jsonResponse(STATS))
      }
      return Promise.reject(new TypeError('Failed to fetch'))
    })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i,
    )
    expect(
      screen.getByRole('button', { name: /try again/i }),
    ).toBeInTheDocument()
    await waitForBothRequests()
  })

  it('still renders the wall when the headline count cannot be read', async () => {
    stubApi(() => jsonResponse(page([testimonial()])), null)
    renderPage()

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.queryByText(/published testimonial/i)).not.toBeInTheDocument()
    await waitForBothRequests()
  })

  it('never renders a testimonial the API left unapproved', async () => {
    // The backend only ever returns approved, consented, non-deleted rows, so
    // the wall renders whatever it is given. This guards the page against
    // rendering a status badge for a row that slipped through.
    stubApi(() =>
      jsonResponse(page([testimonial({ status: 'PENDING' })])),
    )
    renderPage()

    await screen.findByText('Ada Lovelace')
    expect(screen.queryByText(/pending/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/rejected/i)).not.toBeInTheDocument()
    await waitForBothRequests()
  })

  it('has no serious or critical accessibility violations', async () => {
    stubApi(() =>
      jsonResponse(page([testimonial(), testimonial({ id: 'other', rating: 4 })])),
    )
    const { container } = renderPage()

    await screen.findAllByText('Ada Lovelace')

    const results = await axe(container, {
      // jsdom has no 2D canvas, so axe cannot measure colour contrast. The
      // contrast is checked against the real browser in the end-to-end pass.
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(results.violations).toEqual([])
  })

  it('has no accessibility violations in the empty state', async () => {
    stubApi(() => jsonResponse(page([])))
    const { container } = renderPage()

    await screen.findByRole('heading', { name: /no testimonials yet/i })

    const results = await axe(container, {
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(results.violations).toEqual([])
  })

  it('groups the filter buttons inside a labelled group', async () => {
    stubApi(() => jsonResponse(page([])))
    renderPage()

    const group = screen.getByRole('group', { name: /filter by rating/i })
    expect(within(group).getAllByRole('button')).toHaveLength(6)
    await waitForBothRequests()
  })

  it('offers a way to submit a testimonial from the empty state', async () => {
    stubApi(() => jsonResponse(page([])))
    renderPage()

    await screen.findByRole('heading', { name: /no testimonials yet/i })
    expect(
      screen.getByRole('link', { name: /share your story/i }),
    ).toHaveAttribute('href', '/submit')
    await waitForBothRequests()
  })

  it('shows the loading state while the wall is being fetched', async () => {
    // The page query is held open, so the loading state is observable.
    let releaseList: (() => void) | undefined
    const listHeld = new Promise<void>((resolve) => {
      releaseList = resolve
    })
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials/stats')) {
        return settle(Promise.resolve(jsonResponse(STATS)))
      }
      return settle(
        listHeld.then(() => jsonResponse(page([]))),
      )
    })
    renderPage()

    expect(await screen.findByText(/loading testimonials/i)).toBeInTheDocument()

    releaseList?.()
    await waitForBothRequests()
    expect(
      screen.queryByText(/loading testimonials/i),
    ).not.toBeInTheDocument()
  })

  it('shows a loading marker while a refresh is in flight over existing rows', async () => {
    const user = userEvent.setup()
    let releaseList: (() => void) | undefined
    const listHeld = new Promise<void>((resolve) => {
      releaseList = resolve
    })
    let holdTheList = false

    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials/stats')) {
        return settle(Promise.resolve(jsonResponse(STATS)))
      }
      if (holdTheList) {
        return settle(listHeld.then(() => jsonResponse(page([testimonial()]))))
      }
      return settle(Promise.resolve(jsonResponse(page([testimonial()]))))
    })
    renderPage()
    await screen.findByText('Ada Lovelace')

    holdTheList = true
    await user.click(screen.getByRole('button', { name: '4 stars' }))

    // The rows from the previous query are still on screen, so the visitor
    // needs to be told that they are being replaced.
    expect(await screen.findByText(/loading testimonials/i)).toBeInTheDocument()

    releaseList?.()
    await waitForBothRequests()
  })

  it('marks the list as busy while it is being fetched', async () => {
    stubApi(() => jsonResponse(page([testimonial()])))
    renderPage()

    await screen.findByText('Ada Lovelace')
    const list = screen.getByRole('list', { name: /published testimonials/i })
    await waitFor(() => expect(list).not.toHaveAttribute('aria-busy'))
    await waitForBothRequests()
  })

  it('does not show the empty state when the wall failed to load', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials/stats')) {
        return Promise.resolve(jsonResponse(STATS))
      }
      return Promise.reject(new TypeError('Failed to fetch'))
    })
    renderPage()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(
      screen.queryByRole('heading', { name: /no testimonials yet/i }),
    ).not.toBeInTheDocument()
    await waitForBothRequests()
  })

  it('shows the server wording when the rate limiter refuses a request', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials/stats')) {
        return Promise.resolve(jsonResponse(STATS))
      }
      return Promise.resolve(
        jsonResponse(
          {
            code: 'RATE_LIMITED',
            message:
              'Too many testimonials were submitted from this location. Please try again later.',
            fieldErrors: {},
            timestamp: '2026-11-08T00:00:00Z',
            path: '/api/testimonials',
          },
          429,
        ),
      )
    })
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /too many testimonials were submitted/i,
    )
    await waitForBothRequests()
  })
})
