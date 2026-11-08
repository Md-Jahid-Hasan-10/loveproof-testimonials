import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { AuthProvider } from './AuthContext'
import AdminDashboardPage from './AdminDashboardPage'

/**
 * The admin dashboard.
 *
 * The counts come from one request, so they can never disagree with each
 * other. The tests pin that wiring and the failure handling.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const STATS = {
  total: 10,
  pending: 3,
  approved: 5,
  rejected: 2,
  deleted: 4,
  last7Days: 6,
  averageRating: 4.25,
}

describe('the admin dashboard', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderDashboard = () =>
    render(
      <MemoryRouter>
        <AuthProvider>
          <AdminDashboardPage />
        </AuthProvider>
      </MemoryRouter>,
    )

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(jsonResponse(STATS))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDashboard()

    await screen.findByRole('heading', { name: /dashboard/i })

    const results = await axe(container, {
      // jsdom has no 2D canvas, so axe cannot measure colour contrast.
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(results.violations).toEqual([])
  })

  it('shows the moderation counts', async () => {
    renderDashboard()

    expect(
      await screen.findByRole('heading', { name: /dashboard/i }),
    ).toBeInTheDocument()

    // Each tile is a term and its value, so a screen reader announces the
    // label together with the number rather than as a bare figure.
    await waitFor(() => {
      expect(screen.getByText('Pending')).toBeInTheDocument()
      expect(screen.getByText('Approved')).toBeInTheDocument()
      expect(screen.getByText('Rejected')).toBeInTheDocument()
      expect(screen.getByText('Deleted')).toBeInTheDocument()
    })

    expect(screen.getByText('Pending').parentElement).toHaveTextContent('3')
    expect(screen.getByText('Approved').parentElement).toHaveTextContent('5')
    expect(screen.getByText('Rejected').parentElement).toHaveTextContent('2')
    expect(screen.getByText('Deleted').parentElement).toHaveTextContent('4')
  })

  it('shows the total, the recent count and the average rating', async () => {
    renderDashboard()

    await waitFor(() => {
      expect(screen.getByText(/total testimonials/i).closest('div')).toHaveTextContent(
        '10',
      )
      expect(
        screen.getByText(/last 7 days/i).closest('div'),
      ).toHaveTextContent('6')
      expect(
        screen.getByText(/average approved rating/i).closest('div'),
      ).toHaveTextContent('4.3')
    })
  })

  it('reads the counts from the stats endpoint', async () => {
    renderDashboard()

    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(
          ([url]) => url === '/api/admin/testimonials/stats',
        ),
      ).toBe(true)
    })
  })

  it('says so when there are no approved testimonials yet', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ ...STATS, averageRating: null }))

    renderDashboard()

    expect(
      await screen.findByText(/no approved testimonials yet/i),
    ).toBeInTheDocument()
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('reports a failure and offers a retry', async () => {
    fetchMock.mockRejectedValue(new Error('The dashboard is unavailable.'))

    renderDashboard()

    expect(
      await screen.findByText(/the dashboard is unavailable/i),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /try again/i }),
    ).toBeInTheDocument()
  })

  it('links to the moderation inbox', async () => {
    renderDashboard()

    expect(
      await screen.findByRole('link', { name: /open the moderation inbox/i }),
    ).toHaveAttribute('href', '/admin/inbox')
  })
})
