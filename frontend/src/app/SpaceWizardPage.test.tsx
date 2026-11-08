import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import SpaceWizardPage from './SpaceWizardPage'

/**
 * The Space creation wizard.
 *
 * Three things matter here: that the steps move forward and back, that the
 * chosen configuration actually reaches the server, and that the plan limit
 * is reported instead of swallowed.
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
  cancelAtPeriodEnd: false,
  subscriptionPastDue: false,
  currentPeriodEnd: null,
}

const SPACE = {
  id: 'c2b1f6b5-0000-4000-8000-000000000002',
  title: 'Customer reviews',
  subtitle: null,
  slug: 'customer-reviews',
  publicUrl: 'http://localhost:5173/s/customer-reviews',
  fieldCompany: false,
  fieldCompanyRequired: false,
  fieldSocial: false,
  fieldSocialRequired: false,
  fieldWebsite: false,
  fieldWebsiteRequired: false,
  fieldJobTitle: false,
  fieldJobTitleRequired: false,
  ratingEnabled: false,
  theme: 'basic',
  slugLocked: false,
  editable: true,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const THEMES = [
  { key: 'basic', label: 'Basic', premium: false },
  { key: 'bold', label: 'Bold', premium: true },
]

describe('the Space wizard', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderWizard = () =>
    render(
      <MemoryRouter initialEntries={['/app/spaces/new']}>
        <CustomerAuthProvider>
          <Routes>
            <Route path="/app/spaces/new" element={<SpaceWizardPage />} />
            <Route
              path="/app/spaces/:spaceId"
              element={<p>Space page is here</p>}
            />
          </Routes>
        </CustomerAuthProvider>
      </MemoryRouter>,
    )

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/spaces/themes')) {
        return Promise.resolve(jsonResponse(THEMES))
      }
      if (options?.method === 'POST') {
        return Promise.resolve(jsonResponse(SPACE, 201))
      }
      if (options?.method === 'PUT') {
        return Promise.resolve(jsonResponse(SPACE))
      }
      return Promise.resolve(jsonResponse(SPACE))
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('does not let the wizard advance without a title', async () => {
    renderWizard()

    const next = await screen.findByRole('button', { name: 'Next' })
    expect(next).toBeDisabled()

    await userEvent.type(await screen.findByLabelText(/space title/i), 'Reviews')
    await waitFor(() => {
      expect(next).toBeEnabled()
    })
  })

  it('previews the address it would derive from the title', async () => {
    renderWizard()

    await userEvent.type(
      await screen.findByLabelText(/space title/i),
      'Café Love!',
    )

    await waitFor(() => {
      expect(screen.getByLabelText(/public address/i)).toHaveValue('cafe-love')
    })
  })

  it('collects the optional fields and the rating setting', async () => {
    renderWizard()

    await userEvent.type(
      await screen.findByLabelText(/space title/i),
      'Customer reviews',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    await userEvent.click(
      await screen.findByLabelText(/ask for company name/i),
    )
    await userEvent.click(
      screen.getByLabelText(/make company name required/i),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))

    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))

    await userEvent.click(await screen.findByLabelText(/ask for a rating/i))
    await userEvent.click(screen.getByRole('button', { name: /create space/i }))

    await waitFor(() => {
      const put = fetchMock.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === 'PUT',
      )
      expect(put).toBeDefined()
      const body = JSON.parse((put![1] as RequestInit).body as string)
      expect(body.fieldCompany).toBe(true)
      expect(body.fieldCompanyRequired).toBe(true)
      expect(body.ratingEnabled).toBe(true)
    })
  })

  it('takes the owner to the Space once it is created', async () => {
    renderWizard()

    await userEvent.type(
      await screen.findByLabelText(/space title/i),
      'Customer reviews',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: /create space/i }))

    expect(await screen.findByText('Space page is here')).toBeInTheDocument()
  })

  it('marks a premium theme as needing Premium', async () => {
    renderWizard()

    await userEvent.type(
      await screen.findByLabelText(/space title/i),
      'Customer reviews',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))

    expect(await screen.findByText('Needs Premium')).toBeInTheDocument()
    expect(screen.getByLabelText('Bold')).toBeDisabled()
    expect(screen.getByLabelText('Basic')).not.toBeDisabled()
  })

  it('reports the plan limit instead of failing silently', async () => {
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/spaces/themes')) {
        return Promise.resolve(jsonResponse(THEMES))
      }
      if (options?.method === 'POST') {
        return Promise.resolve(
          jsonResponse(
            {
              code: 'PLAN_LIMIT_REACHED',
              message: 'You have reached the limit of 3 Spaces on the free plan.',
            },
            409,
          ),
        )
      }
      return Promise.resolve(jsonResponse(SPACE))
    })

    renderWizard()

    await userEvent.type(
      await screen.findByLabelText(/space title/i),
      'Customer reviews',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await userEvent.click(await screen.findByRole('button', { name: /create space/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/limit of 3 spaces/i)
  })
})
