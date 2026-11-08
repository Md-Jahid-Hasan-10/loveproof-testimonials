import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import SubmitTestimonialPage from './SubmitTestimonialPage'

/**
 * The API is stubbed at the `fetch` boundary rather than at the client module,
 * so the tests exercise the real request building, the real error-envelope
 * parsing and the real form-to-payload mapping.
 */
const fetchMock = vi.fn()

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response
}

function validationFailure(fieldErrors: Record<string, string>): Response {
  return jsonResponse(
    {
      code: 'VALIDATION_FAILED',
      message: 'The submitted testimonial is not valid.',
      fieldErrors,
      timestamp: '2026-11-08T00:00:00Z',
      path: '/api/testimonials',
    },
    400,
  )
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^name/i), 'Ada Lovelace')
  await user.type(screen.getByLabelText(/^role/i), 'Engineer')
  await user.type(screen.getByLabelText(/^company/i), 'Analytical Engines')
  await user.click(screen.getByRole('radio', { name: /5 out of 5/i }))
  await user.type(
    screen.getByLabelText(/your testimonial/i),
    'This testimonial is definitely long enough to pass validation.',
  )
  await user.click(screen.getByRole('checkbox'))
}

describe('SubmitTestimonialPage', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function renderPage() {
    return render(
      <MemoryRouter>
        <SubmitTestimonialPage />
      </MemoryRouter>,
    )
  }

  it('renders the form with every field', () => {
    renderPage()

    expect(
      screen.getByRole('heading', { name: /share your experience/i }),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/^name/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^role/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/^company/i)).toBeInTheDocument()
    expect(screen.getByRole('radiogroup', { name: /rating/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/your testimonial/i)).toBeInTheDocument()
    expect(screen.getByRole('checkbox')).toBeInTheDocument()
  })

  it('shows an inline error on every required field when the form is submitted empty', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(await screen.findByText('Name is required.')).toBeInTheDocument()
    expect(screen.getByText('Rating is required.')).toBeInTheDocument()
    expect(screen.getByText('Testimonial text is required.')).toBeInTheDocument()
    expect(
      screen.getByText('You must consent to the publication of your testimonial.'),
    ).toBeInTheDocument()

    // The API is not called at all when the client-side rules already fail.
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('marks invalid fields with aria-invalid and links the message', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    const name = screen.getByLabelText(/^name/i)
    expect(name).toHaveAttribute('aria-invalid', 'true')
    const describedBy = name.getAttribute('aria-describedby')
    expect(describedBy).toBeTruthy()
    expect(document.getElementById(describedBy as string)).toHaveTextContent(
      'Name is required.',
    )
  })

  it('announces the error summary politely', async () => {
    const user = userEvent.setup()
    const { container } = renderPage()

    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(
      await screen.findByText(/there are 4 problems to fix/i),
    ).toBeInTheDocument()
    // The summary lives in a polite live region, so it is announced without
    // interrupting the user.
    const liveRegion = container.querySelector('[aria-live="polite"]')
    expect(liveRegion).toBeInTheDocument()
    expect(liveRegion).toHaveTextContent(/problems to fix/i)
  })

  it('blocks submission when the consent checkbox is unticked', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText(/^name/i), 'Ada Lovelace')
    await user.click(screen.getByRole('radio', { name: /5 out of 5/i }))
    await user.type(
      screen.getByLabelText(/your testimonial/i),
      'This testimonial is definitely long enough to pass validation.',
    )
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(
      await screen.findByText(
        'You must consent to the publication of your testimonial.',
      ),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('rejects testimonial text that is too short', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText(/^name/i), 'Ada Lovelace')
    await user.click(screen.getByRole('radio', { name: /5 out of 5/i }))
    await user.type(screen.getByLabelText(/your testimonial/i), 'Too short')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(
      await screen.findByText(
        'Testimonial text must be between 20 and 2000 characters.',
      ),
    ).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('requires a rating', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByLabelText(/^name/i), 'Ada Lovelace')
    await user.type(
      screen.getByLabelText(/your testimonial/i),
      'This testimonial is definitely long enough to pass validation.',
    )
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(await screen.findByText('Rating is required.')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('submits a valid testimonial and shows the confirmation', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          id: '3f0c1e6a-0000-4000-8000-000000000001',
          name: 'Ada Lovelace',
          role: 'Engineer',
          company: 'Analytical Engines',
          rating: 5,
          content:
            'This testimonial is definitely long enough to pass validation.',
          status: 'PENDING',
          consentTextVersion: 'v1',
          submittedAt: '2026-11-08T00:00:00Z',
        },
        201,
      ),
    )
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(
      await screen.findByRole('heading', { name: /thank you for your testimonial/i }),
    ).toBeInTheDocument()
    expect(screen.getByText(/awaiting review/i)).toBeInTheDocument()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/testimonials')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({
      name: 'Ada Lovelace',
      role: 'Engineer',
      company: 'Analytical Engines',
      rating: 5,
      content: 'This testimonial is definitely long enough to pass validation.',
      consent: true,
    })
  })

  it('does not show the new testimonial as published', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ id: 'x', status: 'PENDING' }, 201),
    )
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    await screen.findByRole('heading', { name: /thank you for your testimonial/i })
    expect(screen.getByText(/not published yet/i)).toBeInTheDocument()
  })

  it('maps the server field errors onto the matching inputs', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      validationFailure({
        name: 'Name must be between 2 and 100 characters.',
        content: 'Testimonial text must be between 20 and 2000 characters.',
      }),
    )
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(
      await screen.findByText('Name must be between 2 and 100 characters.'),
    ).toBeInTheDocument()
    expect(
      screen.getByText('Testimonial text must be between 20 and 2000 characters.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/^name/i)).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByRole('heading', { name: /thank you/i })).not.toBeInTheDocument()
  })

  it('shows a retryable error banner when the network fails', async () => {
    const user = userEvent.setup()
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i,
    )
    expect(screen.queryByRole('heading', { name: /thank you/i })).not.toBeInTheDocument()
  })

  it('surfaces a duplicate-submission conflict', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'DUPLICATE_SUBMISSION',
          message:
            'This testimonial was already submitted recently. Please wait and try again.',
          fieldErrors: {},
          timestamp: '2026-11-08T00:00:00Z',
          path: '/api/testimonials',
        },
        409,
      ),
    )
    renderPage()

    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /already submitted recently/i,
    )
  })

  it('clears a field error once the visitor edits the field', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))
    expect(await screen.findByText('Name is required.')).toBeInTheDocument()

    await user.type(screen.getByLabelText(/^name/i), 'Ada')
    await waitFor(() => {
      expect(screen.queryByText('Name is required.')).not.toBeInTheDocument()
    })
  })

  it('has no accessibility violations', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'x', status: 'PENDING' }, 201))
    const { container } = renderPage()

    await screen.findByLabelText(/^name/i)

    const results = await axe(container, {
      // jsdom has no 2D canvas, so axe cannot measure colour contrast.
      rules: { 'color-contrast': { enabled: false } },
    })
    expect(results.violations).toEqual([])
  })

  it('is operable with the keyboard alone', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'x', status: 'PENDING' }, 201))
    renderPage()

    // Tab into the form and fill it without touching the mouse.
    await user.type(screen.getByLabelText(/^name/i), 'Ada Lovelace')
    await user.tab()
    await user.tab()
    await user.tab()

    const radioGroup = screen.getByRole('radiogroup', { name: /rating/i })
    expect(radioGroup.contains(document.activeElement)).toBe(true)

    // The arrow keys move between the stars and select the rating.
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('radio', { name: /2 out of 5/i })).toBeChecked()

    await user.type(
      screen.getByLabelText(/your testimonial/i),
      'This testimonial is definitely long enough to pass validation.',
    )
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /submit testimonial/i }))

    expect(
      await screen.findByRole('heading', { name: /thank you for your testimonial/i }),
    ).toBeInTheDocument()
  })
})
