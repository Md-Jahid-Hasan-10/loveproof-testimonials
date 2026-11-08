import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SpaceSubmitPage from './SpaceSubmitPage'

/**
 * The public page of a Space.
 *
 * <p>Three things matter here: that a visitor who has never signed in can
 * submit, that the form only asks for what the Space collects, and that a
 * Space which has stopped collecting says so rather than accepting work it
 * cannot keep.
 */

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })

const SPACE = {
  slug: 'customer-reviews',
  title: 'Customer reviews',
  subtitle: 'What people say',
  theme: 'basic',
  fieldCompany: false,
  fieldCompanyRequired: false,
  fieldSocial: false,
  fieldSocialRequired: false,
  fieldWebsite: false,
  fieldWebsiteRequired: false,
  fieldJobTitle: false,
  fieldJobTitleRequired: false,
  ratingEnabled: false,
  accepting: true,
}

describe('the public Space page', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  const renderPage = () =>
    render(
      <MemoryRouter initialEntries={['/s/customer-reviews']}>
        <Routes>
          <Route path="/s/:slug" element={<SpaceSubmitPage />} />
        </Routes>
      </MemoryRouter>,
    )

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials')) {
        return Promise.resolve(
          jsonResponse(
            { id: 'd3c2a7b6-0000-4000-8000-000000000003', submittedAt: '2026-01-01T00:00:00Z' },
            201,
          ),
        )
      }
      return Promise.resolve(jsonResponse(SPACE))
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows the Space title and subtitle', async () => {
    renderPage()

    expect(
      await screen.findByRole('heading', { name: 'Customer reviews' }),
    ).toBeInTheDocument()
    expect(screen.getByText('What people say')).toBeInTheDocument()
  })

  it('asks only for the fields the Space collects', async () => {
    renderPage()

    await screen.findByRole('heading', { name: 'Customer reviews' })

    expect(screen.getByLabelText(/your name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/your email/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/your testimonial/i)).toBeInTheDocument()
    // The Space does not collect these, so the form does not show them.
    expect(screen.queryByLabelText(/company name/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/job title/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/social media link/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/website url/i)).not.toBeInTheDocument()
  })

  it('shows the optional fields the Space does collect', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials')) {
        return Promise.resolve(jsonResponse({ id: 'x', submittedAt: '2026-01-01T00:00:00Z' }, 201))
      }
      return Promise.resolve(
        jsonResponse({
          ...SPACE,
          fieldCompany: true,
          fieldJobTitle: true,
          ratingEnabled: true,
        }),
      )
    })

    renderPage()

    expect(await screen.findByLabelText(/company name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/job title/i)).toBeInTheDocument()
    expect(screen.getByRole('radiogroup', { name: 'Rating' })).toBeInTheDocument()
  })

  it('lets an anonymous visitor submit and says thank you', async () => {
    renderPage()

    await userEvent.type(
      await screen.findByLabelText(/your name/i),
      'Grace',
    )
    await userEvent.type(screen.getByLabelText(/your email/i), 'grace@example.com')
    await userEvent.type(
      screen.getByLabelText(/your testimonial/i),
      'They did a wonderful job for us.',
    )
    await userEvent.click(
      screen.getByRole('button', { name: /send my testimonial/i }),
    )

    expect(
      await screen.findByRole('heading', { name: /thank you/i }),
    ).toBeInTheDocument()
  })

  it('sends the fields the Space collects', async () => {
    renderPage()

    await userEvent.type(await screen.findByLabelText(/your name/i), 'Grace')
    await userEvent.type(screen.getByLabelText(/your email/i), 'grace@example.com')
    await userEvent.type(
      screen.getByLabelText(/your testimonial/i),
      'They did a wonderful job for us.',
    )
    await userEvent.click(
      screen.getByRole('button', { name: /send my testimonial/i }),
    )

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === 'POST',
      )
      expect(post).toBeDefined()
      const body = JSON.parse((post![1] as RequestInit).body as string)
      expect(body.name).toBe('Grace')
      expect(body.email).toBe('grace@example.com')
      expect(body.content).toBe('They did a wonderful job for us.')
      // The honeypot is empty for a real submission.
      expect(body.website2).toBe('')
    })
  })

  it('refuses to send an incomplete form', async () => {
    renderPage()

    await screen.findByRole('heading', { name: 'Customer reviews' })
    await userEvent.click(
      screen.getByRole('button', { name: /send my testimonial/i }),
    )

    expect(await screen.findByText('Name is required.')).toBeInTheDocument()
    expect(screen.getByText('Email is required.')).toBeInTheDocument()
    expect(
      screen.getByText('Testimonial text is required.'),
    ).toBeInTheDocument()

    const post = fetchMock.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === 'POST',
    )
    expect(post).toBeUndefined()
  })

  it('rejects an email that is not an address', async () => {
    renderPage()

    await userEvent.type(await screen.findByLabelText(/your name/i), 'Grace')
    await userEvent.type(screen.getByLabelText(/your email/i), 'not-an-email')
    await userEvent.type(
      screen.getByLabelText(/your testimonial/i),
      'They did a wonderful job for us.',
    )
    await userEvent.click(
      screen.getByRole('button', { name: /send my testimonial/i }),
    )

    expect(
      await screen.findByText('Email must be a valid email address.'),
    ).toBeInTheDocument()
  })

  it('shows the error when the server refuses the submission', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/testimonials')) {
        return Promise.resolve(
          jsonResponse(
            {
              code: 'SPACE_NOT_ACCEPTING',
              message: 'This Space is not accepting responses at the moment.',
            },
            409,
          ),
        )
      }
      return Promise.resolve(jsonResponse(SPACE))
    })

    renderPage()

    await userEvent.type(await screen.findByLabelText(/your name/i), 'Grace')
    await userEvent.type(screen.getByLabelText(/your email/i), 'grace@example.com')
    await userEvent.type(
      screen.getByLabelText(/your testimonial/i),
      'They did a wonderful job for us.',
    )
    await userEvent.click(
      screen.getByRole('button', { name: /send my testimonial/i }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /not accepting responses/i,
    )
  })

  it('says so when the Space is not collecting', async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(jsonResponse({ ...SPACE, accepting: false })),
    )

    renderPage()

    expect(
      await screen.findByText(/not accepting responses at the moment/i),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /send my testimonial/i }),
    ).not.toBeInTheDocument()
  })

  it('says so when the slug does not exist', async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(
        jsonResponse({ code: 'NOT_FOUND', message: 'This page does not exist.' }, 404),
      ),
    )

    renderPage()

    expect(
      await screen.findByRole('heading', { name: /this page does not exist/i }),
    ).toBeInTheDocument()
  })

  it('stores a submitted script as inert text', async () => {
    renderPage()

    await userEvent.type(await screen.findByLabelText(/your name/i), 'Grace')
    await userEvent.type(screen.getByLabelText(/your email/i), 'grace@example.com')
    await userEvent.type(
      screen.getByLabelText(/your testimonial/i),
      '<script>alert("x")</script> Lovely work',
    )
    await userEvent.click(
      screen.getByRole('button', { name: /send my testimonial/i }),
    )

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(
        (call) => (call[1] as RequestInit | undefined)?.method === 'POST',
      )
      const body = JSON.parse((post![1] as RequestInit).body as string)
      expect(body.content).toBe('<script>alert("x")</script> Lovely work')
    })

    // The text is rendered as text, so no script element reaches the page.
    expect(document.querySelector('script')).toBeNull()
  })
})
