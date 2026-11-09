import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  cancelSubscription,
  createBillingPortalSession,
  createCheckoutSession,
  describeFailure,
  getBilling,
  resumeSubscription,
} from '../api'
import type { Billing, PlanOption } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'

/**
 * How long the page waits between polls while it waits for the webhook.
 *
 * A webhook normally arrives within a second or two, so a short interval
 * keeps the wait feeling immediate without hammering the server.
 */
const POLL_INTERVAL_MS = 3000

/**
 * How many times the page polls before it gives up and asks for a refresh.
 *
 * Fifteen polls at three seconds is forty-five seconds. A payment that has
 * not confirmed by then is not going to confirm on the next poll either.
 */
const MAX_POLLS = 15

/**
 * The billing page: the current plan, what the other plan offers, and the
 * actions a customer can take.
 *
 * <p>The upgrade goes through Stripe Checkout, and the success redirect only
 * starts a poll. It never changes the plan: the plan changes when Stripe's
 * webhook says so, and the server is the only thing that can change it. A
 * customer who reaches the success URL has not paid anything yet as far as
 * this server is concerned, so treating the redirect as a purchase would let
 * anybody upgrade themselves by visiting a URL.
 */
export default function BillingPage() {
  const [searchParams] = useSearchParams()

  const [billing, setBilling] = useState<Billing | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  /**
   * Whether the page is waiting for Stripe to confirm the payment.
   *
   * This is the state the success redirect puts the page into. It is a
   * waiting state, not a confirmation: the plan has not changed yet.
   */
  const [confirming, setConfirming] = useState(
    () => searchParams.get('checkout') === 'success',
  )

  /**
   * Whether the customer came back from the Checkout page without paying.
   */
  const cancelledCheckout =
    searchParams.get('checkout') === 'cancelled' && !confirming

  const pollsRef = useRef(0)

  const load = useCallback(async () => {
    try {
      const loaded = await getBilling()
      setBilling(loaded)
      setError(null)
      return loaded
    } catch (cause) {
      setError(describeFailure(cause, 'Your billing details could not be loaded.'))
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  /**
   * Polls for the plan to change.
   *
   * The webhook may not have arrived yet, so the page keeps asking. It stops
   * as soon as the plan is Premium, and after a fixed number of attempts it
   * stops and asks for a refresh rather than waiting forever.
   *
   * The poll is a chain of timeouts rather than an interval, so a request
   * that is still in flight when the page goes away is abandoned instead of
   * updating a page that is no longer on screen.
   */
  useEffect(() => {
    if (!confirming) {
      return
    }

    let cancelled = false
    let timer: ReturnType<typeof setTimeout>

    const poll = async () => {
      pollsRef.current += 1

      try {
        const loaded = await getBilling()
        if (cancelled) {
          return
        }

        setBilling(loaded)
        if (loaded.plan === 'PREMIUM') {
          setConfirming(false)
          setNotice('Your Premium plan is active. Thank you!')
          return
        }
        if (pollsRef.current >= MAX_POLLS) {
          setConfirming(false)
          setNotice(
            'We have not confirmed your payment yet. It usually takes a few seconds — refresh this page in a moment to see it.',
          )
          return
        }
      } catch {
        // A failed poll is not worth reporting: the page is waiting for a
        // webhook, and the next attempt may well succeed.
        if (cancelled) {
          return
        }
      }

      timer = setTimeout(() => void poll(), POLL_INTERVAL_MS)
    }

    timer = setTimeout(() => void poll(), POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [confirming])

  const handleUpgrade = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const session = await createCheckoutSession()
      // The browser leaves for Stripe's Checkout page. The plan is not
      // changed here; the webhook does that after Stripe confirms the
      // payment.
      window.location.assign(session.url)
    } catch (cause) {
      setError(describeFailure(cause, 'The checkout could not be started.'))
      setBusy(false)
    }
  }

  const handleCancel = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await cancelSubscription()
      setNotice(
        'Your subscription will end at the close of the period you have paid for. You keep Premium until then.',
      )
      await load()
    } catch (cause) {
      setError(describeFailure(cause, 'The subscription could not be cancelled.'))
    } finally {
      setBusy(false)
    }
  }

  const handleResume = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await resumeSubscription()
      setNotice('Your subscription will keep running. Thank you!')
      await load()
    } catch (cause) {
      setError(describeFailure(cause, 'The subscription could not be resumed.'))
    } finally {
      setBusy(false)
    }
  }

  const handlePortal = async () => {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const session = await createBillingPortalSession()
      window.location.assign(session.url)
    } catch (cause) {
      setError(
        describeFailure(
          cause,
          'The billing portal could not be opened.',
          false,
        ),
      )
      setBusy(false)
    }
  }

  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-600">
        Loading your billing details…
      </p>
    )
  }

  if (!billing) {
    return (
      <div className="space-y-4">
        <ErrorBanner
          title="Billing not available"
          message={error ?? 'Your billing details could not be loaded.'}
          onRetry={() => void load()}
        />
      </div>
    )
  }

  const premium = billing.plan === 'PREMIUM'
  const currentOption = billing.plans.find((option) => option.plan === billing.plan)

  return (
    <div className="space-y-6">
      <div>
        <p className="eyebrow">Account</p>
        <h1 className="page-title mt-1.5">Billing</h1>
        <p className="page-subtitle">
          Your plan, what it gives you, and how to change it.
        </p>
      </div>

      {error && (
        <ErrorBanner message={error} onDismiss={() => setError(null)} />
      )}

      {notice && (
        <p
          role="status"
          className="rounded-card border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900"
        >
          {notice}
        </p>
      )}

      {confirming && (
        <p
          role="status"
          aria-live="polite"
          className="rounded-card border border-brand-200 bg-gradient-to-r from-brand-50 to-indigo-50/60 px-4 py-3 text-sm text-brand-900"
        >
          Confirming your upgrade… we are waiting for Stripe to tell us that
          the payment went through. This usually takes a few seconds.
        </p>
      )}

      {cancelledCheckout && (
        <p
          role="status"
          className="rounded-card border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700"
        >
          Nothing has changed — you are still on the {planName(billing)}. You
          can upgrade whenever you are ready.
        </p>
      )}

      {billing.pastDue && (
        <p
          role="status"
          data-testid="past-due-notice"
          className="rounded-card border border-amber-200 bg-gradient-to-r from-amber-50 to-orange-50/60 px-4 py-3 text-sm text-amber-900"
        >
          We could not take your payment. Your Premium access continues while
          we retry — please check your card details so we can charge it.
        </p>
      )}

      <Card className="p-6" elevation="raised">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="section-title">Your plan</h2>
            <p className="mt-1.5 bg-gradient-to-br from-brand-700 to-brand-500 bg-clip-text text-3xl font-bold tracking-tight text-transparent">
              <span data-testid="current-plan">{planName(billing)}</span>
            </p>
            <p className="mt-1.5 text-sm text-slate-600">
              {statusLine(billing)}
            </p>
          </div>

          {currentOption && (
            <p className="pill bg-slate-100 text-slate-600">
              {currentOption.spaces} Spaces · {currentOption.testimonialsPerSpace}{' '}
              testimonials per Space
            </p>
          )}
        </div>

        <div className="mt-6 flex flex-wrap gap-2">
          {!premium && (
            <Button
              loading={busy}
              disabled={!billing.billingConfigured}
              onClick={() => void handleUpgrade()}
            >
              Upgrade to Premium
            </Button>
          )}

          {premium && !billing.cancelAtPeriodEnd && (
            <Button
              variant="secondary"
              loading={busy}
              onClick={() => void handleCancel()}
            >
              Cancel subscription
            </Button>
          )}

          {premium && billing.cancelAtPeriodEnd && (
            <Button loading={busy} onClick={() => void handleResume()}>
              Resume subscription
            </Button>
          )}

          {premium && (
            <Button
              variant="secondary"
              loading={busy}
              onClick={() => void handlePortal()}
            >
              Manage payment details
            </Button>
          )}
        </div>

        {!billing.billingConfigured && (
          <p className="mt-4 text-sm font-medium text-amber-800">
            Billing is not set up on this installation, so the upgrade button
            is unavailable. Everything else works as usual.
          </p>
        )}
      </Card>

      <div className="grid gap-4 sm:grid-cols-2">
        {billing.plans.map((option) => (
          <PlanCard
            key={option.plan}
            option={option}
            current={option.plan === billing.plan}
          />
        ))}
      </div>
    </div>
  )
}

/**
 * One plan, with its features and its limits.
 */
function PlanCard({
  option,
  current,
}: {
  option: PlanOption
  current: boolean
}) {
  return (
    <Card
      className={`p-6 ${current ? 'ring-1 ring-brand-200' : ''}`}
      elevation={current ? 'raised' : 'flat'}
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="section-title">{option.label}</h2>
        {current && (
          <span className="pill bg-brand-50 uppercase tracking-wide text-brand-700">
            Current plan
          </span>
        )}
      </div>

      <p className="mt-1.5 text-sm text-slate-600">{option.price}</p>

      <p className="mt-4 text-sm font-medium text-slate-700">
        {option.spaces} Spaces · {option.testimonialsPerSpace} testimonials per
        Space
      </p>

      <ul className="mt-4 space-y-2">
        {option.features.map((feature) => (
          <li key={feature} className="flex gap-2 text-sm text-slate-700">
            <span
              aria-hidden="true"
              className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-brand-100 text-[0.625rem] font-bold text-brand-700"
            >
              ✓
            </span>
            <span>{feature}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

/**
 * The plan's name.
 *
 * @param billing the billing state
 * @returns the name
 */
function planName(billing: Billing): string {
  const option = billing.plans.find((entry) => entry.plan === billing.plan)
  return option?.label ?? (billing.plan === 'PREMIUM' ? 'Premium' : 'Free')
}

/**
 * The line under the plan's name.
 *
 * It says what is happening to the subscription, which is the thing a
 * customer actually needs to know: whether it is ending, whether a payment
 * failed, or whether everything is fine.
 *
 * @param billing the billing state
 * @returns the line
 */
function statusLine(billing: Billing): string {
  if (billing.subscriptionStatus === 'NONE') {
    return 'You are on the free plan. Nothing is charged.'
  }

  if (billing.pastDue) {
    return 'We could not take your payment. Your access continues while we retry.'
  }

  if (billing.cancelAtPeriodEnd && billing.currentPeriodEnd) {
    return `Your plan ends on ${formatDate(billing.currentPeriodEnd)}. You keep Premium until then.`
  }

  if (billing.cancelAtPeriodEnd) {
    return 'Your plan ends at the close of the period you have paid for.'
  }

  if (billing.subscriptionStatus === 'trialing') {
    return 'Your trial is running.'
  }

  return 'Your subscription is active.'
}

/**
 * Formats an instant as a plain date.
 *
 * The billing page says a date and nothing more: a time would be noise, and
 * the customer's own time zone is not the server's.
 *
 * @param value the instant
 * @returns the date
 */
function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: 'UTC',
  })
}
