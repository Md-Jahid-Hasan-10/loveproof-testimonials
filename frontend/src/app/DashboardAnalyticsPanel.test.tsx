import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardAnalyticsPanel } from './DashboardAnalyticsPanel'

/**
 * The dashboard's analytics panel.
 *
 * The tests are about the things the PRD §4.6 names: the three totals, the
 * chart over a selectable range, the recent submissions, and the upgrade
 * notice that only a Free account sees.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const daily = (days: number, counts: number[] = []) =>
  Array.from({ length: days }, (_, index) => ({
    date: `2026-01-${String(index + 1).padStart(2, '0')}`,
    count: counts[index] ?? 0,
  }))

const DASHBOARD = {
  totalSpaces: 2,
  totalTestimonials: 7,
  uniqueSubmitters: 5,
  daily: daily(30, [1, 0, 2, 0, 0, 0, 4]),
  rangeDays: 30,
  recent: [
    {
      id: 'd3e2f1a0-0000-4000-8000-000000000001',
      spaceId: 'c2b1f6b5-0000-4000-8000-000000000002',
      spaceTitle: 'Ada’s Reviews',
      name: 'Grace',
      content: 'The service was excellent from start to finish.',
      rating: 5,
      submittedAt: '2026-11-07T09:15:00Z',
    },
  ],
  plan: 'FREE',
  spacesLimit: 3,
}

describe('the dashboard analytics', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderPanel = () =>
    render(
      <MemoryRouter initialEntries={['/app']}>
        <DashboardAnalyticsPanel />
      </MemoryRouter>,
    )

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the three totals', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    // The stat labels and their numbers. The numbers are asserted through
    // the stat they sit under, because the same digits also appear in the
    // chart's table.
    expect(await screen.findByText('Testimonials collected')).toBeInTheDocument()
    expect(screen.getByText('Unique submitters')).toBeInTheDocument()
    expect(screen.getByText('Spaces')).toBeInTheDocument()

    expect(
      screen.getByText('Testimonials collected').closest('div'),
    ).toHaveTextContent('7')
    expect(
      screen.getByText('Unique submitters').closest('div'),
    ).toHaveTextContent('5')
    expect(screen.getByText('Spaces').closest('div')).toHaveTextContent('2')
  })

  it('shows how many Spaces the plan allows', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    expect(await screen.findByText('of 3 on your plan')).toBeInTheDocument()
  })

  it('asks for the last 30 days by default', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    await screen.findByText('Testimonials per day')
    const call = fetchMock.mock.calls[0]
    expect(String(call[0])).toContain('/api/app/dashboard')
    expect(String(call[0])).toContain('days=30')
  })

  it('asks for a different range when another one is chosen', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    await screen.findByText('Testimonials per day')
    await userEvent.click(screen.getByRole('button', { name: '7 days' }))

    await waitFor(() => {
      const urls = fetchMock.mock.calls.map((call) => String(call[0]))
      expect(urls.some((url) => url.includes('days=7'))).toBe(true)
    })
  })

  it('marks the chosen range as pressed', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    const thirty = await screen.findByRole('button', { name: '30 days' })
    expect(thirty).toHaveAttribute('aria-pressed', 'true')

    const seven = screen.getByRole('button', { name: '7 days' })
    expect(seven).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(seven)

    await waitFor(() => {
      expect(seven).toHaveAttribute('aria-pressed', 'true')
      expect(thirty).toHaveAttribute('aria-pressed', 'false')
    })
  })

  it('lists the numbers behind the chart for a screen reader', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    // The chart is a picture; the numbers are in a table underneath it.
    expect(
      await screen.findByText(/show the numbers/i),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Testimonials submitted per day'),
    ).toBeInTheDocument()
  })

  it('says so when there were no submissions in the period', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        ...DASHBOARD,
        daily: daily(30),
        totalTestimonials: 0,
        uniqueSubmitters: 0,
      }),
    )

    renderPanel()

    expect(
      await screen.findByText(/no submissions in the last 30 days/i),
    ).toBeInTheDocument()
  })

  it('lists the recent submissions', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    expect(await screen.findByText('Grace')).toBeInTheDocument()
    expect(
      screen.getByText('The service was excellent from start to finish.'),
    ).toBeInTheDocument()
    expect(screen.getByText(/in Ada’s Reviews/)).toBeInTheDocument()
  })

  it('shows the upgrade notice to a Free account', async () => {
    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))

    renderPanel()

    expect(
      await screen.findByText(/the premium plan unlocks more spaces/i),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /see what premium offers/i }),
    ).toBeInTheDocument()
  })

  it('does not show the upgrade notice to a Premium account', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        ...DASHBOARD,
        plan: 'PREMIUM',
        spacesLimit: 25,
      }),
    )

    renderPanel()

    await screen.findByText('Testimonials per day')
    expect(
      screen.queryByText(/the premium plan unlocks more spaces/i),
    ).not.toBeInTheDocument()
  })

  it('reports a failure to load the numbers', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        { code: 'SERVER_ERROR', message: 'Something went wrong.' },
        500,
      ),
    )

    renderPanel()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong.',
    )
  })

  it('retries when the failure is retried', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(
        { code: 'SERVER_ERROR', message: 'Something went wrong.' },
        500,
      ),
    )

    renderPanel()

    await screen.findByRole('alert')

    fetchMock.mockResolvedValue(jsonResponse(DASHBOARD))
    await userEvent.click(screen.getByRole('button', { name: /try again/i }))

    await waitFor(() => {
      expect(screen.getByText('Testimonials per day')).toBeInTheDocument()
    })
  })
})
