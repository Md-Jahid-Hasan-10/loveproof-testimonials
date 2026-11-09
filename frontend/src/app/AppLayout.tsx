import { useCallback, useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { listSpaces } from '../api'
import type { Space } from '../api'
import { BrandMark } from '../components/BrandMark'
import { ErrorBanner } from '../components/ErrorBanner'
import { useCustomer } from './useCustomer'

/**
 * The customer shell: a skip link, a header with the brand and the signed-in
 * customer, a sidebar with the navigation and the Space list, and a footer.
 *
 * The skip link is the first focusable element on the page so a keyboard user
 * can jump straight past the navigation to the content.
 */

/** One entry in the main navigation. */
const NAVIGATION = [
  { to: '/app', label: 'Dashboard', end: true },
  { to: '/app/spaces/new', label: 'New Space', end: false },
  { to: '/app/billing', label: 'Billing', end: false },
  { to: '/app/settings', label: 'Settings', end: false },
]

export function AppLayout() {
  const { customer, signOut } = useCustomer()
  const navigate = useNavigate()

  const [spaces, setSpaces] = useState<Space[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadSpaces = useCallback(async () => {
    try {
      const loaded = await listSpaces()
      setSpaces(loaded)
      setError(null)
    } catch {
      // The sidebar is navigation, not the page's subject. A failure here
      // must not take the whole dashboard down, so the list simply stays
      // empty and the page shows its own failure.
      setSpaces([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadSpaces()
  }, [loadSpaces])

  const handleSignOut = async () => {
    await signOut()
    navigate('/app/sign-in', { replace: true })
  }

  const isPremium = customer?.plan === 'PREMIUM'

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-3 px-4 py-3">
          <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
            <Link to="/app" className="rounded-lg">
              <BrandMark />
            </Link>
            <nav aria-label="Main" className="flex items-center gap-1">
              {NAVIGATION.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `nav-link ${isActive ? 'nav-link-active' : ''}`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {customer && (
              <>
                <span
                  className={`pill ${
                    isPremium
                      ? 'bg-gradient-to-r from-amber-400 to-amber-500 text-amber-950 shadow-sm'
                      : 'bg-slate-100 text-slate-600'
                  }`}
                >
                  {isPremium ? 'Premium' : 'Free'}
                </span>
                <div className="hidden items-center gap-2.5 sm:flex">
                  <span
                    aria-hidden="true"
                    className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700"
                  >
                    {customer.displayName.charAt(0).toUpperCase()}
                  </span>
                  <span className="text-sm text-slate-600">
                    Signed in as{' '}
                    <span className="font-semibold text-slate-900">
                      {customer.displayName}
                    </span>
                  </span>
                </div>
              </>
            )}
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded-control border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 shadow-sm transition hover:border-slate-400 hover:bg-slate-50 hover:text-slate-900"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      {/*
        The past-due banner spans the whole page rather than sitting inside
        the content column: a failed payment is not a billing-page fact, it
        is something the customer needs to know whichever page they are on
        (PRD §4.9).
      */}
      {customer?.subscriptionPastDue && (
        <div className="border-b border-amber-200 bg-amber-50">
          <div className="mx-auto max-w-6xl px-4 py-3">
            <p role="status" className="text-sm text-amber-900">
              We could not take your payment. Your Premium access continues
              while we retry.{' '}
              <Link
                to="/app/billing"
                className="font-semibold text-amber-900 underline"
              >
                Check your card details
              </Link>
              .
            </p>
          </div>
        </div>
      )}

      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-8 px-4 py-8 md:flex-row md:gap-10">
        <aside className="md:w-64 md:shrink-0">
          <div className="surface sticky top-24 p-4">
            <h2 className="px-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
              Your Spaces
            </h2>

            {loading && (
              <p className="mt-3 px-2 text-sm text-slate-500" role="status">
                Loading your Spaces…
              </p>
            )}

            {!loading && spaces.length === 0 && (
              <p className="mt-3 px-2 text-sm leading-relaxed text-slate-500">
                You have not created a Space yet.{' '}
                <Link
                  to="/app/spaces/new"
                  className="font-semibold text-brand-700 underline underline-offset-2 hover:text-brand-800"
                >
                  Create your first one
                </Link>
                .
              </p>
            )}

            {!loading && spaces.length > 0 && (
              <ul className="mt-3 space-y-1">
                {spaces.map((space) => (
                  <li key={space.id}>
                    <NavLink
                      to={`/app/spaces/${space.id}`}
                      className={({ isActive }) =>
                        `block rounded-lg px-2.5 py-2 text-sm transition ${
                          isActive
                            ? 'bg-brand-50 font-semibold text-brand-800 ring-1 ring-inset ring-brand-100'
                            : 'text-slate-700 hover:bg-slate-100'
                        }`
                      }
                    >
                      <span className="block truncate">{space.title}</span>
                      <span className="block truncate font-mono text-xs text-slate-500">
                        /s/{space.slug}
                      </span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </aside>

        {/*
          `tabIndex={-1}` makes the skip link move the focus, not just the
          scroll position: a fragment link on an element that cannot take the
          focus leaves a keyboard user still at the top of the page, with no
          idea where they now are.
        */}
        <main
          id="main"
          tabIndex={-1}
          className="min-w-0 flex-1 focus:outline-none"
        >
          {error && <ErrorBanner message={error} />}
          <Outlet />
        </main>
      </div>

      <footer className="border-t border-slate-200/80 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6">
          <p className="text-sm text-slate-500">
            LoveProof — collect, curate and share your customers&rsquo; words.
          </p>
        </div>
      </footer>
    </div>
  )
}
