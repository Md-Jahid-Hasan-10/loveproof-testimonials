import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * The guard in front of every admin screen.
 *
 * The check is not a security boundary on its own — the server refuses every
 * unauthenticated admin request regardless of what the browser renders. This
 * wrapper only decides what the user *sees*, so that a signed-out visitor is
 * sent to the sign-in form instead of a dashboard that would fail to load.
 */
export function RequireAdmin() {
  const { admin, loading } = useAuth()
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

  if (admin === null) {
    // The attempted location is passed along so the login page can send the
    // visitor back to where they were headed.
    return (
      <Navigate
        to="/admin/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    )
  }

  return <Outlet />
}
