import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ErrorBoundary } from './ErrorBoundary'

/**
 * A component that throws while rendering.
 *
 * The error is thrown on every render, so the boundary's fallback is the only
 * thing that ever gets drawn.
 */
function Exploding(): never {
  throw new Error('The page failed to render.')
}

describe('ErrorBoundary', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('renders its children when nothing goes wrong', () => {
    render(
      <ErrorBoundary>
        <p>The page rendered.</p>
      </ErrorBoundary>,
    )

    expect(screen.getByText('The page rendered.')).toBeInTheDocument()
    expect(
      screen.queryByText(/this page could not be shown/i),
    ).not.toBeInTheDocument()
  })

  it('shows a message instead of a blank page when a render fails', () => {
    // The console error is expected; it would otherwise clutter the test
    // output with the stack trace.
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>,
    )

    expect(
      screen.getByRole('heading', { name: /this page could not be shown/i }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /reload the page/i }),
    ).toBeInTheDocument()
  })

  it('does not leak the internal error message to the visitor', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>,
    )

    expect(
      screen.queryByText(/the page failed to render/i),
    ).not.toBeInTheDocument()
  })

  it('offers a way back', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const reload = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...window.location, reload },
    })

    const user = userEvent.setup()
    render(
      <ErrorBoundary>
        <Exploding />
      </ErrorBoundary>,
    )

    await user.click(screen.getByRole('button', { name: /reload the page/i }))
    expect(reload).toHaveBeenCalled()
  })
})
