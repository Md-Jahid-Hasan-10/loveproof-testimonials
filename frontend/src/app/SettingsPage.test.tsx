import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import SettingsPage from './SettingsPage'

/**
 * The account settings page.
 *
 * Three things are worth checking here: the account's own details are shown
 * and can be changed, the password form is only offered to an account that
 * has a password, and a failure is reported rather than swallowed.
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
  hasPassword: true,
  plan: 'FREE',
  subscriptionStatus: 'NONE',
  cancelAtPeriodEnd: false,
  subscriptionPastDue: false,
  currentPeriodEnd: null,
}

describe('the account settings page', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderSettings = () =>
    render(
      <MemoryRouter initialEntries={['/app/settings']}>
        <CustomerAuthProvider>
          <SettingsPage />
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

  it('shows the email address and whether it is verified', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    expect(await screen.findByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByText('Verified')).toBeInTheDocument()
  })

  it('says when the address is not verified yet', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse({ ...CUSTOMER, emailVerified: false }))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    expect(
      await screen.findByText(/this address is not verified yet/i),
    ).toBeInTheDocument()
    expect(screen.queryByText('Verified')).not.toBeInTheDocument()
  })

  it('fills the display name in from the account', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    const field = await screen.findByLabelText(/display name/i)
    expect(field).toHaveValue('Ada')
  })

  it('saves a new display name', async () => {
    const profileCalls: Array<{ url: string; body: unknown }> = []

    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/auth/profile')) {
        profileCalls.push({ url, body: JSON.parse(String(options?.body)) })
        return Promise.resolve(jsonResponse({ ...CUSTOMER, displayName: 'Ada L.' }))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    const field = await screen.findByLabelText(/display name/i)
    await userEvent.clear(field)
    await userEvent.type(field, 'Ada L.')

    await userEvent.click(screen.getByRole('button', { name: /save name/i }))

    await waitFor(() => {
      expect(profileCalls).toHaveLength(1)
    })
    expect(profileCalls[0].body).toMatchObject({ displayName: 'Ada L.' })
    expect(
      await screen.findByText('Your name has been updated.'),
    ).toBeInTheDocument()
  })

  it('reports a failure to save the display name', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/auth/profile')) {
        return Promise.resolve(
          jsonResponse(
            { code: 'VALIDATION_FAILED', message: 'That name is too long.' },
            400,
          ),
        )
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    await userEvent.click(
      await screen.findByRole('button', { name: /save name/i }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That name is too long.',
    )
  })

  it('offers the password form to an account that has a password', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    expect(
      await screen.findByLabelText(/current password/i),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/^new password/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/confirm new password/i)).toBeInTheDocument()
  })

  it('does not offer the password form to a Google-only account', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(
          jsonResponse({ ...CUSTOMER, hasPassword: false }),
        )
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    expect(
      await screen.findByText(/you sign in with google/i),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText(/current password/i)).not.toBeInTheDocument()
  })

  it('refuses to change the password when the two do not match', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    await userEvent.type(
      await screen.findByLabelText(/current password/i),
      'old-password',
    )
    await userEvent.type(screen.getByLabelText(/^new password/i), 'new-password')
    await userEvent.type(
      screen.getByLabelText(/confirm new password/i),
      'different-password',
    )

    await userEvent.click(
      screen.getByRole('button', { name: /change password/i }),
    )

    expect(
      await screen.findByText('The two passwords do not match.'),
    ).toBeInTheDocument()
    // Nothing was sent: the check happens before the request.
    const changeCalls = fetchMock.mock.calls.filter((call) =>
      String(call[0]).includes('/app/auth/change-password'),
    )
    expect(changeCalls).toHaveLength(0)
  })

  it('changes the password', async () => {
    const changeCalls: Array<{ url: string; body: unknown }> = []

    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/auth/change-password')) {
        changeCalls.push({ url, body: JSON.parse(String(options?.body)) })
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    await userEvent.type(
      await screen.findByLabelText(/current password/i),
      'old-password',
    )
    await userEvent.type(screen.getByLabelText(/^new password/i), 'new-password')
    await userEvent.type(
      screen.getByLabelText(/confirm new password/i),
      'new-password',
    )

    await userEvent.click(
      screen.getByRole('button', { name: /change password/i }),
    )

    await waitFor(() => {
      expect(changeCalls).toHaveLength(1)
    })
    expect(changeCalls[0].body).toMatchObject({
      currentPassword: 'old-password',
      newPassword: 'new-password',
    })
    expect(
      await screen.findByText('Your password has been changed.'),
    ).toBeInTheDocument()
  })

  it('reports a failure to change the password', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/auth/change-password')) {
        return Promise.resolve(
          jsonResponse(
            {
              code: 'INVALID_CREDENTIALS',
              message: 'Your current password is wrong.',
            },
            400,
          ),
        )
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderSettings()

    await userEvent.type(
      await screen.findByLabelText(/current password/i),
      'wrong-password',
    )
    await userEvent.type(screen.getByLabelText(/^new password/i), 'new-password')
    await userEvent.type(
      screen.getByLabelText(/confirm new password/i),
      'new-password',
    )

    await userEvent.click(
      screen.getByRole('button', { name: /change password/i }),
    )

    expect(
      await screen.findByText('Your current password is wrong.'),
    ).toBeInTheDocument()
  })
})
