import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import CustomerRegisterPage from './CustomerRegisterPage'

/**
 * The customer's registration page.
 *
 * Registering has to do two things: create the account, and leave the new
 * customer signed in. The second half is what the page depends on -- it
 * redirects to the dashboard on success, and every call the dashboard makes
 * needs a session the backend established during the registration itself.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const CUSTOMER = {
  id: 'b1a0f5a4-0000-4000-8000-000000000002',
  email: 'ada@example.com',
  displayName: 'Ada',
  emailVerified: false,
  hasPassword: true,
  plan: 'FREE',
  subscriptionStatus: 'NONE',
  cancelAtPeriodEnd: false,
  subscriptionPastDue: false,
  currentPeriodEnd: null,
}

describe('the registration page', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderRegister = () =>
    render(
      <MemoryRouter initialEntries={['/app/register']}>
        <CustomerAuthProvider>
          <CustomerRegisterPage />
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

  it('creates the account', async () => {
    const registerCalls: Array<{ url: string; body: unknown }> = []

    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/register')) {
        registerCalls.push({ url, body: JSON.parse(String(options?.body)) })
        return Promise.resolve(jsonResponse(CUSTOMER, 201))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderRegister()

    await userEvent.type(
      await screen.findByLabelText(/email address/i),
      'ada@example.com',
    )
    await userEvent.type(screen.getByLabelText(/^password/i), 'correct horse battery staple')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(registerCalls).toHaveLength(1)
    })
    expect(registerCalls[0].body).toMatchObject({
      email: 'ada@example.com',
      password: 'correct horse battery staple',
    })
  })

  it('sends no display name when the field is left blank', async () => {
    const registerCalls: Array<{ url: string; body: unknown }> = []

    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/register')) {
        registerCalls.push({ url, body: JSON.parse(String(options?.body)) })
        return Promise.resolve(jsonResponse(CUSTOMER, 201))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderRegister()

    await userEvent.type(
      await screen.findByLabelText(/email address/i),
      'ada@example.com',
    )
    await userEvent.type(screen.getByLabelText(/^password/i), 'correct horse battery staple')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(registerCalls).toHaveLength(1)
    })
    expect(registerCalls[0].body).toMatchObject({ displayName: null })
  })

  it('refuses a password shorter than eight characters before asking the server', async () => {
    renderRegister()

    await userEvent.type(
      await screen.findByLabelText(/email address/i),
      'ada@example.com',
    )
    await userEvent.type(screen.getByLabelText(/^password/i), 'short')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(
      await screen.findByText('A password must be at least 8 characters long.'),
    ).toBeInTheDocument()
    // Nothing was sent to the server: the check happens before the request.
    // The provider does read the session on mount, so the assertion is
    // about the registration call specifically.
    const registerCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).includes('/app/auth/register'),
    )
    expect(registerCalls).toHaveLength(0)
  })

  it('reports a failure to register', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/register')) {
        return Promise.resolve(
          jsonResponse(
            {
              code: 'CONFLICT',
              message: 'An account with that email already exists.',
            },
            409,
          ),
        )
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderRegister()

    await userEvent.type(
      await screen.findByLabelText(/email address/i),
      'ada@example.com',
    )
    await userEvent.type(screen.getByLabelText(/^password/i), 'correct horse battery staple')
    await userEvent.click(screen.getByRole('button', { name: /create account/i }))

    expect(
      await screen.findByText('An account with that email already exists.'),
    ).toBeInTheDocument()
  })
})
