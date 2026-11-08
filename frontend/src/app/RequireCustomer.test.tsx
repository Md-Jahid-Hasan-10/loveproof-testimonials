import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import { RequireCustomer } from './RequireCustomer'

/**
 * The guard in front of the customer screens.
 *
 * The guard decides what the visitor sees. It is not the security boundary —
 * the server refuses every unauthenticated customer request regardless — but
 * it must never show the dashboard to somebody who is not signed in.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('the customer route guard', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderGuard = () =>
    render(
      <MemoryRouter initialEntries={['/app']}>
        <CustomerAuthProvider>
          <Routes>
            <Route element={<RequireCustomer />}>
              <Route path="/app" element={<p>Dashboard is here</p>} />
            </Route>
            <Route path="/app/sign-in" element={<p>Sign in page</p>} />
          </Routes>
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

  it('sends a signed-out visitor to the sign-in page', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
    )

    renderGuard()

    expect(await screen.findByText('Sign in page')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard is here')).not.toBeInTheDocument()
  })

  it('shows the dashboard to a signed-in customer', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        id: 'b1a0f5a4-0000-4000-8000-000000000001',
        email: 'ada@example.com',
        displayName: 'Ada',
        emailVerified: true,
    hasPassword: true,
        plan: 'FREE',
        subscriptionStatus: 'NONE',
        cancelAtPeriodEnd: false,
        subscriptionPastDue: false,
        currentPeriodEnd: null,
      }),
    )

    renderGuard()

    expect(await screen.findByText('Dashboard is here')).toBeInTheDocument()
    expect(screen.queryByText('Sign in page')).not.toBeInTheDocument()
  })

  it('waits for the session to be checked before deciding', async () => {
    fetchMock.mockReturnValue(new Promise(() => {}))

    renderGuard()

    expect(screen.getByText(/checking your session/i)).toBeInTheDocument()
    expect(screen.queryByText('Sign in page')).not.toBeInTheDocument()
  })
})
