import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import SpaceEmbedPage from './SpaceEmbedPage'

/**
 * The embed configuration page.
 *
 * The page is where the owner turns a Space into something they can put on
 * their own website, so the tests are about the premium lock, the live
 * preview and the snippet that gets copied.
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

const PREMIUM_CUSTOMER = { ...CUSTOMER, plan: 'PREMIUM' }

const CONFIG = {
  spaceId: 'c2b1f6b5-0000-4000-8000-000000000002',
  embedToken: 'a-token-1234',
  layout: 'masonry',
  themeMode: 'light',
  backgroundColor: null,
  animation: false,
  scriptUrl: 'http://localhost:5173/embed/loveproof.js',
  dataUrl: 'http://localhost:5173/api/app/public/embeds/a-token-1234',
}

const PUBLIC_EMBED = {
  title: 'Ada’s Reviews',
  subtitle: 'What people say',
  layout: 'masonry',
  themeMode: 'light',
  backgroundColor: null,
  animation: false,
  testimonials: [
    {
      name: 'Grace',
      content: 'Lovely work.',
      rating: 5,
      company: null,
      jobTitle: null,
      website: null,
      socialLink: null,
    },
  ],
}

describe('the embed configuration page', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderEmbed = () =>
    render(
      <MemoryRouter initialEntries={['/app/spaces/space-1/embed']}>
        <CustomerAuthProvider>
          <Routes>
            <Route
              path="/app/spaces/:spaceId/embed"
              element={<SpaceEmbedPage />}
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
      return options?.method === method && String(call[0]).includes(contains)
    })

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (options?.method === 'PUT') {
        return Promise.resolve(jsonResponse(CONFIG))
      }
      if (options?.method === 'POST') {
        return Promise.resolve(
          jsonResponse({ ...CONFIG, embedToken: 'a-new-token' }),
        )
      }
      if (url.includes('/public/embeds/')) {
        return Promise.resolve(jsonResponse(PUBLIC_EMBED))
      }
      return Promise.resolve(jsonResponse(CONFIG))
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the snippet the owner pastes into their website', async () => {
    renderEmbed()

    expect(
      await screen.findByText(/<script src="http:\/\/localhost:5173\/embed\/loveproof.js" async><\/script>/),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/<div data-loveproof-embed="a-token-1234"><\/div>/),
    ).toBeInTheDocument()
  })

  it('previews the testimonials the embed will show', async () => {
    renderEmbed()

    expect(await screen.findByText('Lovely work.')).toBeInTheDocument()
    expect(screen.getByText('Grace')).toBeInTheDocument()
  })

  it('locks the premium options for a Free account', async () => {
    renderEmbed()

    expect(await screen.findByLabelText('Carousel')).toBeDisabled()
    expect(await screen.findByLabelText('Animation')).toBeDisabled()
    expect(await screen.findByLabelText(/background colour/i)).toBeDisabled()
  })

  it('unlocks the premium options for a Premium account', async () => {
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(PREMIUM_CUSTOMER))
      }
      if (options?.method === 'PUT') {
        return Promise.resolve(jsonResponse({ ...CONFIG, layout: 'carousel' }))
      }
      if (url.includes('/public/embeds/')) {
        return Promise.resolve(jsonResponse(PUBLIC_EMBED))
      }
      return Promise.resolve(jsonResponse(CONFIG))
    })

    renderEmbed()

    expect(await screen.findByLabelText('Carousel')).toBeEnabled()
    expect(await screen.findByLabelText('Animation')).toBeEnabled()
  })

  it('saves the layout', async () => {
    // The carousel is a Premium option, so the account has to be Premium
    // for it to be chosen at all.
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(PREMIUM_CUSTOMER))
      }
      if (options?.method === 'PUT') {
        return Promise.resolve(jsonResponse({ ...CONFIG, layout: 'carousel' }))
      }
      if (url.includes('/public/embeds/')) {
        return Promise.resolve(jsonResponse(PUBLIC_EMBED))
      }
      return Promise.resolve(jsonResponse(CONFIG))
    })

    renderEmbed()

    await userEvent.click(await screen.findByLabelText('Carousel'))
    await userEvent.click(
      await screen.findByRole('button', { name: /save the embed/i }),
    )

    await waitFor(() => {
      const put = requestFor('PUT', '/embed')
      expect(put).toBeDefined()
      const body = JSON.parse((put![1] as RequestInit).body as string)
      expect(body.layout).toBe('carousel')
    })
  })

  it('saves the theme mode', async () => {
    renderEmbed()

    await userEvent.click(await screen.findByLabelText('Dark'))
    await userEvent.click(
      await screen.findByRole('button', { name: /save the embed/i }),
    )

    await waitFor(() => {
      const put = requestFor('PUT', '/embed')
      const body = JSON.parse((put![1] as RequestInit).body as string)
      expect(body.themeMode).toBe('dark')
    })
  })

  it('reports a refusal from the server', async () => {
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (options?.method === 'PUT') {
        return Promise.resolve(
          jsonResponse(
            {
              code: 'CONFLICT',
              message: 'The carousel needs a Premium plan. Upgrade to use it.',
            },
            409,
          ),
        )
      }
      if (url.includes('/public/embeds/')) {
        return Promise.resolve(jsonResponse(PUBLIC_EMBED))
      }
      return Promise.resolve(jsonResponse(CONFIG))
    })

    renderEmbed()

    await userEvent.click(
      await screen.findByRole('button', { name: /save the embed/i }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /needs a premium plan/i,
    )
  })

  it('generates a new token and says the snippet has to be updated', async () => {
    renderEmbed()

    await userEvent.click(
      await screen.findByRole('button', { name: /generate a new token/i }),
    )

    expect(
      await screen.findByText(/update the snippet on your website/i),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/<div data-loveproof-embed="a-new-token"><\/div>/),
    ).toBeInTheDocument()
  })

  it('says so when the embed cannot be loaded', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(
        jsonResponse({ code: 'NOT_FOUND', message: 'That Space does not exist.' }, 404),
      )
    })

    renderEmbed()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /that space does not exist/i,
    )
  })
})
