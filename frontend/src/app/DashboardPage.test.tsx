import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import DashboardPage from './DashboardPage'

/**
 * The dashboard: the customer's Spaces.
 *
 * The empty state, the populated state and the plan-limit failure are the
 * three things worth checking here -- the last one because the server has to
 * be allowed to say "upgrade" without the interface swallowing the message.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const CUSTOMER = {
  id: 'b1a0f5a4-0000-4000-8000-000000000001',
  email: 'ada@example.com',
  displayName: 'Ada',
  emailVerified: true,
  plan: 'FREE',
  subscriptionStatus: 'NONE',
  cancelAtPeriodEnd: false,
  subscriptionPastDue: false,
  currentPeriodEnd: null,
}

const SPACE = {
  id: 'c2b1f6b5-0000-4000-8000-000000000002',
  title: 'Ada’s Reviews',
  subtitle: 'What people say',
  slug: 'adas-reviews',
  publicUrl: 'http://localhost:5173/s/adas-reviews',
  fieldCompany: false,
  fieldCompanyRequired: false,
  fieldSocial: false,
  fieldSocialRequired: false,
  fieldWebsite: false,
  fieldWebsiteRequired: false,
  fieldJobTitle: false,
  fieldJobTitleRequired: false,
  ratingEnabled: true,
  theme: 'basic',
  slugLocked: false,
  editable: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

describe('the dashboard', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderDashboard = () =>
    render(
      <MemoryRouter initialEntries={['/app']}>
        <CustomerAuthProvider>
          <DashboardPage />
        </CustomerAuthProvider>
      </MemoryRouter>,
    )

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the empty state when there are no Spaces', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderDashboard()

    expect(await screen.findByText('No Spaces yet')).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /create your first space/i }),
    ).toBeInTheDocument()
  })

  it('lists the Spaces with their public URLs', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([SPACE]))
    })

    renderDashboard()

    expect(await screen.findByText('Ada’s Reviews')).toBeInTheDocument()
    expect(
      screen.getByText('http://localhost:5173/s/adas-reviews'),
    ).toBeInTheDocument()
  })

  it('marks a read-only Space as read-only', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([{ ...SPACE, editable: false }]))
    })

    renderDashboard()

    expect(await screen.findByText('Read-only')).toBeInTheDocument()
    // A read-only Space cannot be deleted.
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  it('shows the failure when the Spaces cannot be loaded', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(
        jsonResponse(
          { code: 'SERVER_ERROR', message: 'Something went wrong.' },
          500,
        ),
      )
    })

    renderDashboard()

    expect(await screen.findByRole('alert')).toBeInTheDocument()
  })

  it('asks for confirmation before deleting a Space', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)

    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (options?.method === 'DELETE') {
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return Promise.resolve(jsonResponse([SPACE]))
    })

    renderDashboard()

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))

    expect(confirmSpy).toHaveBeenCalled()
    // The Space is still there because the confirmation was declined.
    await waitFor(() => {
      expect(screen.getByText('Ada’s Reviews')).toBeInTheDocument()
    })

    confirmSpy.mockRestore()
  })

  it('removes the Space once the deletion is confirmed', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)

    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (options?.method === 'DELETE') {
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return Promise.resolve(jsonResponse([SPACE]))
    })

    renderDashboard()

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))

    await waitFor(() => {
      expect(screen.queryByText('Ada’s Reviews')).not.toBeInTheDocument()
    })

    confirmSpy.mockRestore()
  })
})
