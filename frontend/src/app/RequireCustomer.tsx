import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCustomer } from './useCustomer'

/**
 * The guard in front of every customer screen.
 *
 * The check is not a security boundary on its own — the server refuses every
 * unauthenticated customer request regardless of what the browser renders.
 * This wrapper only decides what the user *sees*, so that a signed-out
 * visitor is sent to the sign-in form instead of a dashboard that would fail
 * to load.
 */
export function RequireCustomer() {
  const { customer, loading } = useCustomer()
  const location = useLocation()

  if (loading) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex min-h-screen items-center justify-center bg-slate-50"
      >
        <p className="text-sm text-slate-600">Checking your session…</p>
      </div>
    )
  }

  if (customer === null) {
    // The attempted location is passed along so the sign-in page can send the
    // visitor back to where they were headed.
    return (
      <Navigate
        to="/app/sign-in"
        replace
        state={{ from: location.pathname + location.search }}
      />
    )
  }

  return <Outlet />
}
