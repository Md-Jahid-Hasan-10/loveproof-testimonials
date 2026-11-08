import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { AuthProvider } from './AuthContext'
import AdminInboxPage from './AdminInboxPage'

/**
 * The moderation inbox.
 *
 * The actions are the point of the screen, so the tests pin what each button
 * sends to the server: the path, the method and the body. A mistake here is
 * silent — the list would simply stop updating.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const PENDING = {
  id: '11111111-1111-1111-1111-111111111111',
  name: 'Jane Doe',
  role: 'Head of Support',
  company: 'Acme Corp',
  rating: 5,
  content: 'The onboarding was genuinely smooth.',
  status: 'PENDING' as const,
  consentTextVersion: 'v1',
  submittedAt: '2026-11-01T10:00:00Z',
}

const APPROVED = {
  ...PENDING,
  id: '22222222-2222-2222-2222-222222222222',
  name: 'Bob Jones',
  status: 'APPROVED' as const,
}

const page = (content: unknown[]) =>
  jsonResponse({
    content,
    page: 0,
    size: 20,
    total: content.length,
    totalPages: 1,
  })

describe('the moderation inbox', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderInbox = () =>
    render(
      <MemoryRouter>
        <AuthProvider>
          <AdminInboxPage />
        </AuthProvider>
      </MemoryRouter>,
    )

  beforeEach(() => {
    fetchMock = vi.fn().mockResolvedValue(page([PENDING]))
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists the pending testimonials', async () => {
    renderInbox()

    expect(
      await screen.findByRole('heading', { name: /moderation inbox/i }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Jane Doe')).toBeInTheDocument()
    expect(screen.getByText(/the onboarding was genuinely smooth/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderInbox()

    await screen.findByRole('heading', { name: /moderation inbox/i })

    const results = await axe(container, {
      // jsdom has no 2D canvas, so axe cannot measure colour contrast.
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(results.violations).toEqual([])
  })

  it('asks for the pending status by default', async () => {
    renderInbox()

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) =>
        String(url).includes('/api/admin/testimonials?'),
      )
      expect(String(call?.[0])).toContain('status=PENDING')
    })
  })

  it('approves a pending testimonial', async () => {
    const user = userEvent.setup()

    fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/approve')) {
        return Promise.resolve(jsonResponse({ ...PENDING, status: 'APPROVED' }))
      }
      return Promise.resolve(page([PENDING]))
    })

    renderInbox()

    await screen.findByText('Jane Doe')
    await user.click(screen.getByRole('button', { name: /approve/i }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) =>
        String(url).includes('/approve'),
      )
      expect(String(call?.[0])).toBe(
        `/api/admin/testimonials/${PENDING.id}/approve`,
      )
      expect(call?.[1].method).toBe('POST')
    })
  })

  it('rejects a testimonial only after a reason is given', async () => {
    const user = userEvent.setup()

    fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/reject')) {
        return Promise.resolve(jsonResponse({ ...PENDING, status: 'REJECTED' }))
      }
      return Promise.resolve(page([PENDING]))
    })

    renderInbox()

    await screen.findByText('Jane Doe')
    await user.click(screen.getByRole('button', { name: /reject/i }))

    // The reason field appears, and an empty reason is refused locally.
    const reasonField = await screen.findByLabelText(/reason for rejecting/i)
    await user.click(screen.getByRole('button', { name: /save reason/i }))

    expect(
      await screen.findByText(/a rejection reason is required/i),
    ).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.some(([url]) => String(url).includes('/reject')),
    ).toBe(false)

    await user.type(reasonField, 'Off topic.')
    await user.click(screen.getByRole('button', { name: /save reason/i }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) =>
        String(url).includes('/reject'),
      )
      expect(String(call?.[0])).toBe(
        `/api/admin/testimonials/${PENDING.id}/reject`,
      )
      expect(JSON.parse(call?.[1].body)).toEqual({ reason: 'Off topic.' })
    })
  })

  it('deletes an approved testimonial', async () => {
    const user = userEvent.setup()

    fetchMock.mockImplementation((_url: string, options: { method?: string }) => {
      if (options?.method === 'DELETE') {
        return Promise.resolve(jsonResponse(APPROVED))
      }
      return Promise.resolve(page([APPROVED]))
    })

    renderInbox()

    await screen.findByText('Bob Jones')
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(
        ([, options]) => options?.method === 'DELETE',
      )
      expect(String(call?.[0])).toBe(
        `/api/admin/testimonials/${APPROVED.id}`,
      )
    })
  })

  it('offers no approve button on an approved testimonial', async () => {
    fetchMock.mockResolvedValue(page([APPROVED]))

    renderInbox()

    await screen.findByText('Bob Jones')
    expect(screen.queryByRole('button', { name: /approve/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^delete$/i })).toBeInTheDocument()
  })

  it('switches the status filter', async () => {
    const user = userEvent.setup()

    renderInbox()

    await screen.findByText('Jane Doe')
    await user.click(screen.getByRole('tab', { name: /approved/i }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) =>
        String(url).includes('status=APPROVED'),
      )
      expect(call).toBeDefined()
    })
  })

  it('searches by name, company and text', async () => {
    const user = userEvent.setup()

    renderInbox()

    await screen.findByText('Jane Doe')
    await user.type(screen.getByLabelText(/search/i), 'acme')
    await user.click(screen.getByRole('button', { name: /search/i }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([url]) =>
        String(url).includes('q=acme'),
      )
      expect(call).toBeDefined()
    })
  })

  it('reports a failure and offers a retry', async () => {
    fetchMock.mockRejectedValue(new Error('The inbox is unavailable.'))

    renderInbox()

    expect(
      await screen.findByText(/the inbox is unavailable/i),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument()
  })

  it('shows the rating as stars', async () => {
    renderInbox()

    const rating = await screen.findByLabelText('5 out of 5 stars')
    expect(rating).toBeInTheDocument()
  })

  it('announces the moderation status of each testimonial', async () => {
    renderInbox()

    await screen.findByText('Jane Doe')
    // "Pending" appears twice — as the filter tab and as the row's status
    // badge — so the badge is picked out of the testimonial's own row.
    const row = screen.getByText('Jane Doe').closest('li')
    expect(row).not.toBeNull()
    expect(within(row as HTMLElement).getByText('Pending')).toBeInTheDocument()
  })

  it('does not claim the filter matched nothing when the inbox failed to load', async () => {
    fetchMock.mockRejectedValue(new Error('The inbox is unavailable.'))

    renderInbox()

    expect(
      await screen.findByText(/the inbox is unavailable/i),
    ).toBeInTheDocument()
    // The empty state would contradict the error banner above it.
    expect(
      screen.queryByText(/nothing matches this filter/i),
    ).not.toBeInTheDocument()
  })

  it('shows the loading state while the inbox is being fetched', async () => {
    let releaseList: (() => void) | undefined
    const listHeld = new Promise<void>((resolve) => {
      releaseList = resolve
    })
    fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/api/admin/testimonials?')) {
        return listHeld.then(() => page([PENDING]))
      }
      return Promise.resolve(jsonResponse({}))
    })

    renderInbox()

    expect(await screen.findByText(/loading the inbox/i)).toBeInTheDocument()

    releaseList?.()
    await screen.findByText('Jane Doe')
  })

  it('keeps only the selected tab in the tab order', async () => {
    renderInbox()

    await screen.findByText('Jane Doe')

    const pending = screen.getByRole('tab', { name: /pending/i })
    const approved = screen.getByRole('tab', { name: /approved/i })

    expect(pending).toHaveAttribute('tabindex', '0')
    expect(approved).toHaveAttribute('tabindex', '-1')
  })

  it('points the tabs at the panel they control', async () => {
    renderInbox()

    await screen.findByText('Jane Doe')

    const tab = screen.getByRole('tab', { name: /pending/i })
    const controls = tab.getAttribute('aria-controls')
    expect(controls).toBeTruthy()
    expect(document.getElementById(controls as string)).toHaveAttribute(
      'role',
      'tabpanel',
    )
  })
})
