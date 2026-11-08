import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import SpaceDetailPage from './SpaceDetailPage'

/**
 * The Space editor.
 *
 * The editor is where a downgraded owner finds out that a Space has become
 * read-only, so that case is checked here along with the ordinary save.
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

const THEMES = [
  { key: 'basic', label: 'Basic', premium: false },
  { key: 'bold', label: 'Bold', premium: true },
]

describe('the Space editor', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderDetail = () =>
    render(
      <MemoryRouter initialEntries={['/app/spaces/space-1']}>
        <CustomerAuthProvider>
          <Routes>
            <Route path="/app/spaces/:spaceId" element={<SpaceDetailPage />} />
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
      if (options?.method === 'PUT') {
        return Promise.resolve(jsonResponse(SPACE))
      }
      return Promise.resolve(jsonResponse(SPACE))
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the Space and its public address', async () => {
    renderDetail()

    expect(await screen.findByText('Ada’s Reviews')).toBeInTheDocument()
    expect(
      screen.getByText('http://localhost:5173/s/adas-reviews'),
    ).toBeInTheDocument()
  })

  it('saves the edited details', async () => {
    renderDetail()

    const title = await screen.findByLabelText(/space title/i)
    await userEvent.clear(title)
    await userEvent.type(title, 'Ada Reviews')
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => {
      const put = fetchMock.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === 'PUT',
      )
      expect(put).toBeDefined()
      const body = JSON.parse((put![1] as RequestInit).body as string)
      expect(body.title).toBe('Ada Reviews')
      expect(body.ratingEnabled).toBe(true)
    })

    expect(await screen.findByText(/your changes were saved/i)).toBeInTheDocument()
  })

  it('turns off the requirement when the field itself is turned off', async () => {
    renderDetail()

    await userEvent.click(
      await screen.findByLabelText(/ask for company name/i),
    )
    await userEvent.click(
      await screen.findByLabelText(/make company name required/i),
    )
    await userEvent.click(screen.getByLabelText(/ask for company name/i))

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }))

    await waitFor(() => {
      const put = fetchMock.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === 'PUT',
      )
      const body = JSON.parse((put![1] as RequestInit).body as string)
      expect(body.fieldCompany).toBe(false)
      expect(body.fieldCompanyRequired).toBe(false)
    })
  })

  it('explains that a read-only Space cannot be changed', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/spaces/themes')) {
        return Promise.resolve(jsonResponse(THEMES))
      }
      return Promise.resolve(jsonResponse({ ...SPACE, editable: false }))
    })

    renderDetail()

    expect(
      await screen.findByText(/this space is read-only/i),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /save changes/i }),
    ).not.toBeInTheDocument()
    expect(await screen.findByLabelText(/space title/i)).toBeDisabled()
  })

  it('locks the address once the Space has received a testimonial', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/spaces/themes')) {
        return Promise.resolve(jsonResponse(THEMES))
      }
      return Promise.resolve(jsonResponse({ ...SPACE, slugLocked: true }))
    })

    renderDetail()

    expect(
      await screen.findByText(/cannot change any more/i),
    ).toBeInTheDocument()
    expect(await screen.findByLabelText(/public address/i)).toBeDisabled()
  })

  it('says so when the Space does not exist', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/spaces/themes')) {
        return Promise.resolve(jsonResponse(THEMES))
      }
      return Promise.resolve(
        jsonResponse({ code: 'NOT_FOUND', message: 'Space not found.' }, 404),
      )
    })

    renderDetail()

    expect(await screen.findByRole('alert')).toHaveTextContent(/space not found/i)
    expect(
      screen.getByRole('link', { name: /back to your spaces/i }),
    ).toBeInTheDocument()
  })
})
