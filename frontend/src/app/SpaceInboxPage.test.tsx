import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import SpaceInboxPage from './SpaceInboxPage'

/**
 * The Space inbox.
 *
 * The inbox is where the owner curates, so the tests here are about the
 * curation actions: favouriting, the Wall of Love, hiding, editing and
 * deleting -- and about what a read-only Space is allowed to do.
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
  subtitle: null,
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

const GRACE = {
  id: 'd3c2f7c6-0000-4000-8000-000000000003',
  name: 'Grace',
  email: 'grace@example.com',
  content: 'Lovely work.',
  rating: 5,
  company: null,
  socialLink: null,
  website: null,
  jobTitle: null,
  consentGiven: true,
  favorite: false,
  wallOfLove: false,
  visible: true,
  submittedAt: '2026-01-02T00:00:00Z',
}

const ALAN = {
  ...GRACE,
  id: 'e4d3a8d7-0000-4000-8000-000000000004',
  name: 'Alan',
  email: 'alan@example.com',
  content: 'Fine, thank you.',
  submittedAt: '2026-01-03T00:00:00Z',
}

const page = (content: unknown[]) => ({
  content,
  page: 0,
  size: 20,
  total: content.length,
  totalPages: 1,
})

describe('the Space inbox', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderInbox = () =>
    render(
      <MemoryRouter initialEntries={['/app/spaces/space-1/inbox']}>
        <CustomerAuthProvider>
          <Routes>
            <Route
              path="/app/spaces/:spaceId/inbox"
              element={<SpaceInboxPage />}
            />
          </Routes>
        </CustomerAuthProvider>
      </MemoryRouter>,
    )

  /**
   * The request the page made to the given endpoint.
   *
   * @param method the HTTP method
   * @param contains a fragment of the path
   */
  const requestFor = (method: string, contains: string) =>
    fetchMock.mock.calls.find((call) => {
      const options = call[1] as RequestInit | undefined
      return (
        options?.method === method &&
        String(call[0]).includes(contains)
      )
    })

  /**
   * The most recent listing request.
   *
   * The page asks for the inbox again whenever a filter changes, so the
   * latest call is the one that carries the filter being checked.
   */
  const lastListRequest = () =>
    fetchMock.mock.calls
      .filter((call) => String(call[0]).includes('/testimonials'))
      .at(-1)

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (options?.method === 'PATCH') {
        return Promise.resolve(jsonResponse({ ...GRACE, favorite: true }))
      }
      if (options?.method === 'PUT') {
        return Promise.resolve(jsonResponse({ ...GRACE, content: 'Edited.' }))
      }
      if (options?.method === 'DELETE') {
        return new Response(null, { status: 204 })
      }
      if (url.includes('/testimonials')) {
        return Promise.resolve(jsonResponse(page([GRACE, ALAN])))
      }
      return Promise.resolve(jsonResponse(SPACE))
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('lists the Space’s testimonials', async () => {
    renderInbox()

    expect(await screen.findByText('Grace')).toBeInTheDocument()
    expect(screen.getByText('grace@example.com')).toBeInTheDocument()
    expect(screen.getByText('Lovely work.')).toBeInTheDocument()
  })

  it('favourites a testimonial', async () => {
    renderInbox()

    await userEvent.click(
      await screen.findByRole('button', { name: /add grace to favourites/i }),
    )

    await waitFor(() => {
      const patch = requestFor('PATCH', '/testimonials/')
      expect(patch).toBeDefined()
      const body = JSON.parse((patch![1] as RequestInit).body as string)
      expect(body).toEqual({ favorite: true })
    })
  })

  it('adds a testimonial to the Wall of Love', async () => {
    renderInbox()

    await userEvent.click(
      await screen.findByRole('button', {
        name: /add grace to the wall of love/i,
      }),
    )

    await waitFor(() => {
      const patch = requestFor('PATCH', '/testimonials/')
      expect(patch).toBeDefined()
      const body = JSON.parse((patch![1] as RequestInit).body as string)
      expect(body).toEqual({ wallOfLove: true })
    })
  })

  it('hides a testimonial', async () => {
    renderInbox()

    await userEvent.click(
      await screen.findByRole('button', { name: /hide grace/i }),
    )

    await waitFor(() => {
      const patch = requestFor('PATCH', '/testimonials/')
      expect(patch).toBeDefined()
      const body = JSON.parse((patch![1] as RequestInit).body as string)
      expect(body).toEqual({ visible: false })
    })
  })

  it('edits the text of a testimonial', async () => {
    renderInbox()

    await userEvent.click(
      await screen.findByRole('button', { name: /edit grace/i }),
    )

    const textarea = await screen.findByLabelText(/testimonial text/i)
    await userEvent.clear(textarea)
    await userEvent.type(textarea, 'Edited.')
    await userEvent.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() => {
      const put = requestFor('PUT', '/testimonials/')
      expect(put).toBeDefined()
      const body = JSON.parse((put![1] as RequestInit).body as string)
      expect(body).toEqual({ content: 'Edited.' })
    })
  })

  it('asks before deleting, and deletes after the confirmation', async () => {
    renderInbox()

    await userEvent.click(
      await screen.findByRole('button', { name: /delete grace/i }),
    )

    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText(/delete this testimonial/i),
    ).toBeInTheDocument()
    expect(requestFor('DELETE', '/testimonials/')).toBeUndefined()

    await userEvent.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    await waitFor(() => {
      expect(requestFor('DELETE', '/testimonials/')).toBeDefined()
    })
  })

  it('leaves a delete alone when the confirmation is cancelled', async () => {
    renderInbox()

    await userEvent.click(
      await screen.findByRole('button', { name: /delete grace/i }),
    )
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', {
        name: /cancel/i,
      }),
    )

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })
    expect(requestFor('DELETE', '/testimonials/')).toBeUndefined()
  })

  it('searches the name, the email and the text', async () => {
    renderInbox()

    await userEvent.type(
      await screen.findByLabelText(/^search$/i),
      'grace',
    )
    await userEvent.click(screen.getByRole('button', { name: /^search$/i }))

    await waitFor(() => {
      expect(String(lastListRequest()![0])).toContain('search=grace')
    })
  })

  it('filters by favourites', async () => {
    renderInbox()

    await userEvent.click(await screen.findByLabelText(/favourites/i))

    await waitFor(() => {
      expect(String(lastListRequest()![0])).toContain('favorite=true')
    })
  })

  it('says so when a read-only Space cannot be changed', async () => {
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (options?.method === 'PATCH') {
        return Promise.resolve(jsonResponse(GRACE))
      }
      if (url.includes('/testimonials')) {
        return Promise.resolve(jsonResponse(page([GRACE, ALAN])))
      }
      return Promise.resolve(jsonResponse({ ...SPACE, editable: false }))
    })

    renderInbox()

    expect(
      await screen.findByText(/this space is read-only/i),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('button', { name: /add grace to favourites/i }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: /delete grace/i }),
    ).toBeDisabled()
  })

  it('reports a failure to load the inbox', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/testimonials')) {
        return Promise.resolve(
          jsonResponse(
            { code: 'NOT_FOUND', message: 'That Space does not exist.' },
            404,
          ),
        )
      }
      return Promise.resolve(jsonResponse(SPACE))
    })

    renderInbox()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /that space does not exist/i,
    )
  })
})
