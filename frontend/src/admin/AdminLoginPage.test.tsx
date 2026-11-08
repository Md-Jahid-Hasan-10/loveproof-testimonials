import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ApiError } from '../api'
import { AuthProvider } from './AuthContext'
import AdminLoginPage from './AdminLoginPage'

/**
 * The admin sign-in page.
 *
 * The behaviour that matters: the credentials are sent to the server rather
 * than checked in the browser, a failure is reported without saying which
 * half was wrong, and a successful sign-in lands on the admin surface.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

describe('the admin sign-in page', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderPage = (initialPath = '/admin/login') =>
    render(
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthProvider>
          <Routes>
            <Route path="/admin/login" element={<AdminLoginPage />} />
            <Route
              path="/admin"
              element={<p>Dashboard is here</p>}
            />
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

  it('renders the sign-in form', async () => {
    // The session check on mount.
    fetchMock.mockResolvedValue(
      jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
    )

    renderPage()

    expect(
      await screen.findByRole('heading', { name: /admin sign in/i }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
    )

    const { container } = renderPage()

    await screen.findByRole('heading', { name: /admin sign in/i })

    const results = await axe(container, {
      // jsdom has no 2D canvas, so axe cannot measure colour contrast.
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(results.violations).toEqual([])
  })

  it('signs in with the submitted credentials and lands on the admin surface', async () => {
    const user = userEvent.setup()

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
      )
      .mockResolvedValueOnce(
        jsonResponse({
          username: 'admin',
          displayName: 'LoveProof Admin',
          role: 'ADMIN',
          lastLoginAt: null,
        }),
      )

    renderPage()

    await screen.findByLabelText(/username/i)
    await user.type(screen.getByLabelText(/username/i), 'admin')
    await user.type(screen.getByLabelText(/password/i), 'loveproof')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByText(/dashboard is here/i)).toBeInTheDocument()

    const loginCall = fetchMock.mock.calls.find(
      ([url, options]) =>
        url === '/api/admin/session' && options?.method === 'POST',
    )
    expect(loginCall).toBeDefined()
    expect(JSON.parse(loginCall?.[1].body)).toEqual({
      username: 'admin',
      password: 'loveproof',
    })
  })

  it('reports a wrong password without saying which half was wrong', async () => {
    const user = userEvent.setup()

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: 'AUTHENTICATION_FAILED',
            message: 'Invalid username or password.',
            fieldErrors: {},
          },
          401,
        ),
      )

    renderPage()

    await screen.findByLabelText(/username/i)
    await user.type(screen.getByLabelText(/username/i), 'admin')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(
      await screen.findByText(/invalid username or password/i),
    ).toBeInTheDocument()
  })

  it('stays on the sign-in page when the credentials are refused', async () => {
    const user = userEvent.setup()

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: 'AUTHENTICATION_FAILED',
            message: 'Invalid username or password.',
            fieldErrors: {},
          },
          401,
        ),
      )

    renderPage()

    await screen.findByLabelText(/username/i)
    await user.type(screen.getByLabelText(/username/i), 'admin')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /admin sign in/i }),
      ).toBeInTheDocument()
    })
  })

  it('does not reveal whether an account exists', async () => {
    const user = userEvent.setup()

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            code: 'AUTHENTICATION_FAILED',
            message: 'Invalid username or password.',
            fieldErrors: {},
          },
          401,
        ),
      )

    renderPage()

    await screen.findByLabelText(/username/i)
    await user.type(screen.getByLabelText(/username/i), 'nobody')
    await user.type(screen.getByLabelText(/password/i), 'loveproof')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(
      await screen.findByText(/invalid username or password/i),
    ).toBeInTheDocument()
    expect(screen.queryByText(/unknown user/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/no such user/i)).not.toBeInTheDocument()
  })

  it('surfaces a network failure', async () => {
    const user = userEvent.setup()

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ code: 'UNAUTHENTICATED', message: 'No session.' }, 401),
      )
      .mockRejectedValueOnce(new Error('The network is unreachable.'))

    renderPage()

    await screen.findByLabelText(/username/i)
    await user.type(screen.getByLabelText(/username/i), 'admin')
    await user.type(screen.getByLabelText(/password/i), 'loveproof')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(
      await screen.findByText(/the network is unreachable/i),
    ).toBeInTheDocument()
  })
})

describe('the ApiError', () => {
  it('reports whether the failure was a validation failure', () => {
    const error = new ApiError(400, undefined, 'fallback')
    expect(error.isValidationFailure).toBe(true)
  })
})
