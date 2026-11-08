import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { CustomerAuthProvider } from './CustomerAuthContext'
import BillingPage from './BillingPage'

/**
 * The billing page.
 *
 * The tests are about the three things the page has to get right:
 *
 * <ul>
 *   <li>It shows the plan and what the other plan offers.</li>
 *   <li>It says what is happening to the subscription — a cancellation date,
 *       a failed payment — because that is what the customer came to read.</li>
 *   <li>The success redirect does **not** change anything. It starts a poll
 *       and waits for the webhook, because a customer who reaches the success
 *       URL has not paid anything yet as far as the server is concerned.</li>
 * </ul>
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

const PLANS = [
  {
    plan: 'FREE',
    label: 'Free',
    price: 'Free',
    spaces: 3,
    testimonialsPerSpace: 100,
    features: ['3 Spaces', '100 testimonials per Space', 'Masonry embed'],
  },
  {
    plan: 'PREMIUM',
    label: 'Premium',
    price: '$9.99/month',
    spaces: 25,
    testimonialsPerSpace: 1000,
    features: [
      '25 Spaces',
      '1000 testimonials per Space',
      'Premium themes',
      'Carousel embed',
    ],
  },
]

/**
 * How long the page waits between polls.
 *
 * Mirrors the page's own interval. The test drives the clock itself, so the
 * two must agree.
 */
const POLL_INTERVAL_MS = 3000

const FREE_BILLING = {
  plan: 'FREE',
  subscriptionStatus: 'NONE',
  cancelAtPeriodEnd: false,
  pastDue: false,
  currentPeriodEnd: null,
  billingConfigured: true,
  plans: PLANS,
}

const PREMIUM_BILLING = {
  ...FREE_BILLING,
  plan: 'PREMIUM',
  subscriptionStatus: 'active',
}

const CANCELLING_BILLING = {
  ...PREMIUM_BILLING,
  cancelAtPeriodEnd: true,
  currentPeriodEnd: '2026-12-12T00:00:00Z',
}

const PAST_DUE_BILLING = {
  ...PREMIUM_BILLING,
  subscriptionStatus: 'past_due',
  pastDue: true,
}

describe('the billing page', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  let billing: unknown
  let assignMock: ReturnType<typeof vi.fn>

  const renderBilling = (path = '/app/billing') =>
    render(
      <MemoryRouter initialEntries={[path]}>
        <CustomerAuthProvider>
          <Routes>
            <Route path="/app/billing" element={<BillingPage />} />
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

  /**
   * Waits for something to appear, advancing the faked clock as it goes.
   *
   * The clock is faked so the poll interval can be driven by the test
   * rather than by wall-clock time. `waitFor` on its own would wait on the
   * faked clock's `setTimeout`, which never fires unless something advances
   * it, so this helper advances it a little on every attempt.
   *
   * @param expectation what to wait for
   */
  const waitForVisible = async (expectation: () => void) => {
    for (let attempt = 0; attempt < 50; attempt++) {
      try {
        expectation()
        return
      } catch {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(50)
        })
      }
    }
    expectation()
  }

  /**
   * Waits for the page's first load to finish.
   *
   * The page and the session provider both load something on mount, and
   * their updates land after the render returns. Letting them settle
   * inside `act` keeps the assertions that follow from racing the first
   * paint, and keeps the state updates inside the test's own act scope.
   */
  const settle = async () => {
    await act(async () => {
      // Two ticks: one for the mocked fetch to resolve, one for the state
      // update that follows it.
      await Promise.resolve()
      await Promise.resolve()
    })
  }

  /**
   * Advances the faked clock and lets the page's state updates land.
   *
   * The poll's updates happen inside a timer callback, so the clock has to
   * move inside an act scope for React to accept them.
   *
   * @param milliseconds how far to move the clock
   */
  const advance = async (milliseconds: number) => {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(milliseconds)
    })
  }

  beforeEach(() => {
    billing = FREE_BILLING
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockImplementation((url: string, options?: RequestInit) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      if (url.includes('/app/billing') && options?.method === 'POST') {
        if (url.includes('/checkout')) {
          return Promise.resolve(
            jsonResponse({ url: 'https://checkout.stripe.test/session' }),
          )
        }
        if (url.includes('/portal')) {
          return Promise.resolve(
            jsonResponse({ url: 'https://billing.stripe.test/session' }),
          )
        }
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (url.includes('/app/billing')) {
        return Promise.resolve(jsonResponse(billing))
      }
      return Promise.resolve(jsonResponse([]))
    })

    // jsdom refuses to navigate, and its `location.assign` cannot be
    // redefined. The whole location object is replaced instead, so the page
    // can call `assign` without the browser acting on it.
    assignMock = vi.fn()
    vi.stubGlobal('location', {
      assign: assignMock,
      href: 'http://localhost/app/billing',
      search: '',
      pathname: '/app/billing',
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('shows the current plan and what each plan gives you', async () => {
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(screen.getByTestId('current-plan')).toHaveTextContent('Free')
    })

    expect(screen.getByText('3 Spaces')).toBeInTheDocument()
    expect(screen.getByText('25 Spaces')).toBeInTheDocument()
    expect(screen.getByText('$9.99/month')).toBeInTheDocument()
    expect(screen.getByText('Current plan')).toBeInTheDocument()
  })

  it('offers the upgrade to a customer on the free plan', async () => {
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Upgrade to Premium' }),
      ).toBeInTheDocument()
    })
    expect(
      screen.queryByRole('button', { name: 'Cancel subscription' }),
    ).not.toBeInTheDocument()
  })

  it('sends the browser to Stripe when the customer upgrades', async () => {
    const user = userEvent.setup()
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Upgrade to Premium' }),
      ).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Upgrade to Premium' }))

    await waitFor(() => {
      expect(assignMock).toHaveBeenCalledWith(
        'https://checkout.stripe.test/session',
      )
    })
    expect(requestFor('POST', '/app/billing/checkout')).toBeDefined()
  })

  it('offers the cancel action to a premium customer', async () => {
    billing = PREMIUM_BILLING
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Cancel subscription' }),
      ).toBeInTheDocument()
    })
    expect(
      screen.queryByRole('button', { name: 'Resume subscription' }),
    ).not.toBeInTheDocument()
  })

  it('asks Stripe to cancel at the period end and says the plan continues', async () => {
    const user = userEvent.setup()
    billing = PREMIUM_BILLING
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Cancel subscription' }),
      ).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: 'Cancel subscription' }))

    await waitFor(() => {
      expect(requestFor('POST', '/app/billing/cancel')).toBeDefined()
    })
    expect(
      await screen.findByText(/You keep Premium until then/),
    ).toBeInTheDocument()
  })

  it('shows the end date and a resume button when the plan is ending', async () => {
    billing = CANCELLING_BILLING
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Resume subscription' }),
      ).toBeInTheDocument()
    })
    expect(
      screen.queryByRole('button', { name: 'Cancel subscription' }),
    ).not.toBeInTheDocument()
    expect(screen.getByText(/Your plan ends on/)).toBeInTheDocument()
  })

  it('asks Stripe to resume when the customer resumes', async () => {
    const user = userEvent.setup()
    billing = CANCELLING_BILLING
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: 'Resume subscription' }),
      ).toBeInTheDocument()
    })

    await user.click(
      screen.getByRole('button', { name: 'Resume subscription' }),
    )

    await waitFor(() => {
      expect(requestFor('POST', '/app/billing/resume')).toBeDefined()
    })
  })

  it('shows the past-due message when a payment failed', async () => {
    billing = PAST_DUE_BILLING
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(screen.getByTestId('past-due-notice')).toBeInTheDocument()
    })
    expect(screen.getByTestId('past-due-notice')).toHaveTextContent(
      'We could not take your payment',
    )
  })

  it('waits for the webhook after the success redirect', async () => {
    vi.useFakeTimers()
    renderBilling('/app/billing?checkout=success&session_id=cs_test')
    await settle()

    await waitForVisible(() => {
      expect(
        screen.getByText(/Confirming your upgrade/),
      ).toBeInTheDocument()
    })

    // The plan has not changed: the redirect is not a purchase.
    expect(screen.getByTestId('current-plan')).toHaveTextContent('Free')
  })

  it('confirms the upgrade once the webhook has been applied', async () => {
    vi.useFakeTimers()
    renderBilling('/app/billing?checkout=success&session_id=cs_test')
    await settle()

    await waitForVisible(() => {
      expect(
        screen.getByText(/Confirming your upgrade/),
      ).toBeInTheDocument()
    })

    // The webhook arrives and the account becomes Premium.
    billing = PREMIUM_BILLING
    await advance(POLL_INTERVAL_MS)

    await waitForVisible(() => {
      expect(
        screen.getByText(/Your Premium plan is active/),
      ).toBeInTheDocument()
    })
    expect(
      screen.queryByText(/Confirming your upgrade/),
    ).not.toBeInTheDocument()
  })

  it('stops polling and asks for a refresh when the payment is not confirmed', async () => {
    vi.useFakeTimers()
    renderBilling('/app/billing?checkout=success&session_id=cs_test')
    await settle()

    await waitForVisible(() => {
      expect(
        screen.getByText(/Confirming your upgrade/),
      ).toBeInTheDocument()
    })

    // The webhook never arrives. The page gives up rather than waiting
    // forever, and says so rather than spinning for ever.
    await advance(POLL_INTERVAL_MS * 20)

    await waitForVisible(() => {
      expect(
        screen.getByText(/We have not confirmed your payment yet/),
      ).toBeInTheDocument()
    })
    expect(
      screen.queryByText(/Confirming your upgrade/),
    ).not.toBeInTheDocument()
  })

  it('says nothing changed when the customer comes back without paying', async () => {
    renderBilling('/app/billing?checkout=cancelled')
    await settle()

    await waitFor(() => {
      expect(screen.getByText(/Nothing has changed/)).toBeInTheDocument()
    })
    expect(
      screen.queryByText(/Confirming your upgrade/),
    ).not.toBeInTheDocument()
  })

  it('explains that billing is not set up when Stripe is not configured', async () => {
    billing = { ...FREE_BILLING, billingConfigured: false }
    renderBilling()
    await settle()

    await waitFor(() => {
      expect(
        screen.getByText(/Billing is not set up/),
      ).toBeInTheDocument()
    })
    expect(
      screen.getByRole('button', { name: 'Upgrade to Premium' }),
    ).toBeDisabled()
  })

  it('shows the failure when the billing details cannot be loaded', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/app/auth/me')) {
        return Promise.resolve(jsonResponse(CUSTOMER))
      }
      return Promise.resolve(
        jsonResponse(
          { code: 'SERVER_ERROR', message: 'The server is unavailable.' },
          500,
        ),
      )
    })

    renderBilling()
    await settle()

    await waitFor(() => {
      expect(screen.getByText('The server is unavailable.')).toBeInTheDocument()
    })
  })
})
