import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * The admin shell: a skip link, a header with the brand, the admin
 * navigation and the signed-in administrator, and a footer.
 *
 * The skip link is the first focusable element on the page so a keyboard user
 * can jump straight past the navigation to the dashboard.
 */
export function AdminLayout() {
  const { admin, signOut } = useAuth()
  const navigate = useNavigate()

  const handleSignOut = async () => {
    await signOut()
    navigate('/admin/login', { replace: true })
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
          <div className="flex items-center gap-6">
            <Link
              to="/admin"
              className="text-lg font-bold tracking-tight text-slate-900"
            >
              LoveProof
              <span className="ml-2 rounded-md bg-slate-100 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-slate-600">
                Admin
              </span>
            </Link>
            <nav aria-label="Admin" className="flex items-center gap-1">
              <NavLink
                to="/admin"
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
                to="/admin/inbox"
                className={({ isActive }) =>
                  `rounded-lg px-3 py-2 text-sm font-medium ${
                    isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                  }`
                }
              >
                Inbox
              </NavLink>
            </nav>
          </div>

          <div className="flex items-center gap-3">
            {admin && (
              <p className="text-sm text-slate-600">
                Signed in as{' '}
                <span className="font-medium text-slate-900">
                  {admin.displayName}
                </span>
              </p>
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

      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6">
          <p className="text-sm text-slate-500">
            LoveProof Admin — moderation is recorded in the audit log.
          </p>
        </div>
      </footer>
    </div>
  )
}
