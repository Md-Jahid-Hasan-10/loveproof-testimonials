import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

/**
 * The API is stubbed at the `fetch` boundary so the routing test exercises the
 * real client.
 */
const fetchMock = vi.fn()

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

describe('App', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders the LoveProof brand', () => {
    render(<App />)

    expect(screen.getByRole('link', { name: 'LoveProof' })).toBeInTheDocument()
  })

  it('renders the landing page with a heading', () => {
    render(<App />)

    expect(
      screen.getByRole('heading', { name: /testimonials that build trust/i }),
    ).toBeInTheDocument()
  })

  it('exposes a skip-to-content link', () => {
    render(<App />)

    expect(
      screen.getByRole('link', { name: /skip to content/i }),
    ).toBeInTheDocument()
  })

  it('links to the submission form', () => {
    render(<App />)

    expect(
      screen.getAllByRole('link', { name: /share your story/i }).length,
    ).toBeGreaterThan(0)
  })

  it('links to the public wall from the landing page', () => {
    render(<App />)

    expect(
      screen.getAllByRole('link', { name: /read the wall/i }).length,
    ).toBeGreaterThan(0)
  })

  it('navigates to the public wall', async () => {
    const user = userEvent.setup()
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/admin/session')) {
        // No session: the visitor is not signed in.
        return Promise.resolve(
          jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
        )
      }
      if (url.includes('/testimonials/stats')) {
        return Promise.resolve(jsonResponse({ count: 0, averageRating: null }))
      }
      return Promise.resolve(
        jsonResponse({
          content: [],
          page: 0,
          size: 12,
          total: 0,
          totalPages: 0,
        }),
      )
    })
    render(<App />)

    await user.click(screen.getByRole('link', { name: /read the wall/i }))

    expect(
      await screen.findByRole('heading', { name: /what our customers say/i }),
    ).toBeInTheDocument()
  })
})
