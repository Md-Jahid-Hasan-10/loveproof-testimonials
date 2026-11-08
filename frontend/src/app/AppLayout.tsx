import { useCallback, useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { listSpaces } from '../api'
import type { Space } from '../api'
import { ErrorBanner } from '../components/ErrorBanner'
import { useCustomer } from './useCustomer'

/**
 * The customer shell: a skip link, a header with the brand and the signed-in
 * customer, a sidebar with the navigation and the Space list, and a footer.
 *
 * The skip link is the first focusable element on the page so a keyboard user
 * can jump straight past the navigation to the content.
 */
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

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to content
      </a>

      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-4">
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
            <Link
              to="/app"
              className="text-lg font-bold tracking-tight text-slate-900"
            >
              LoveProof
            </Link>
            <nav aria-label="Main" className="flex items-center gap-1">
              <NavLink
                to="/app"
                end
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium ${
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                Dashboard
              </NavLink>
              <NavLink
                to="/app/spaces/new"
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium ${
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                New Space
              </NavLink>
              <NavLink
                to="/app/billing"
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium ${
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                Billing
              </NavLink>
            </nav>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {customer && (
              <>
                <span className="rounded-md bg-slate-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-slate-600">
                  {customer.plan === 'PREMIUM' ? 'Premium' : 'Free'}
                </span>
                <p className="text-sm text-slate-600">
                  Signed in as{' '}
                  <span className="font-medium text-slate-900">
                    {customer.displayName}
                  </span>
                </p>
              </>
            )}
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-slate-900 ring-1 ring-inset ring-slate-300 hover:bg-slate-50"
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

      <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-8 px-4 py-8 md:flex-row">
        <aside className="md:w-64 md:shrink-0">
          <nav aria-label="Your Spaces">
            <h2 className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Your Spaces
            </h2>

            {loading && (
              <p className="mt-3 text-sm text-slate-500" role="status">
                Loading your Spaces…
              </p>
            )}

            {!loading && spaces.length === 0 && (
              <p className="mt-3 text-sm text-slate-500">
                You have not created a Space yet.{' '}
                <Link
                  to="/app/spaces/new"
                  className="font-medium text-brand-700 underline"
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
                        `block rounded-lg px-3 py-2 text-sm ${
                          isActive
                            ? 'bg-brand-50 font-medium text-brand-700'
                            : 'text-slate-700 hover:bg-slate-100'
                        }`
                      }
                    >
                      <span className="block truncate">{space.title}</span>
                      <span className="block truncate text-xs text-slate-500">
                        /s/{space.slug}
                      </span>
                    </NavLink>
                  </li>
                ))}
              </ul>
            )}
          </nav>
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

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6">
          <p className="text-sm text-slate-500">
            LoveProof — collect, curate and share your customers&rsquo; words.
          </p>
        </div>
      </footer>
    </div>
  )
}
