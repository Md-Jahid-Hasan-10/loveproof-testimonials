import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthContext'
import { RequireAdmin } from './RequireAdmin'

/**
 * The guard in front of the admin screens.
 *
 * The guard decides what the visitor sees. It is not the security boundary —
 * the server refuses every unauthenticated admin request regardless — but it
 * must never show the dashboard to somebody who is not signed in.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('the admin route guard', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderGuard = () =>
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <AuthProvider>
          <Routes>
            <Route element={<RequireAdmin />}>
              <Route path="/admin" element={<p>Dashboard is here</p>} />
            </Route>
            <Route path="/admin/login" element={<p>Sign in page</p>} />
          </Routes>
        </AuthProvider>
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

  it('shows the admin screen to a signed-in administrator', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        username: 'admin',
        displayName: 'LoveProof Admin',
        role: 'ADMIN',
        lastLoginAt: null,
      }),
    )

    renderGuard()

    expect(await screen.findByText('Dashboard is here')).toBeInTheDocument()
    expect(screen.queryByText('Sign in page')).not.toBeInTheDocument()
  })

  it('waits for the session to be checked before deciding', async () => {
    fetchMock.mockReturnValue(new Promise(() => {}))

    renderGuard()

    expect(
      screen.getByText(/checking your session/i),
    ).toBeInTheDocument()
    expect(screen.queryByText('Sign in page')).not.toBeInTheDocument()
  })
})
