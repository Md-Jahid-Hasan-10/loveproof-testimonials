import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { BrandMark } from '../components/BrandMark'
import { useAuth } from './useAuth'

/**
 * The admin shell: a skip link, a header with the brand, the admin
 * navigation and the signed-in administrator, and a footer.
 *
 * The skip link is the first focusable element on the page so a keyboard user
 * can jump straight past the navigation to the dashboard.
 */

/** One entry in the admin navigation. */
const NAVIGATION = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/inbox', label: 'Inbox', end: false },
]

export function AdminLayout() {
  const { admin, signOut } = useAuth()
  const navigate = useNavigate()

  const handleSignOut = async () => {
    await signOut()
    navigate('/admin/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-4 py-3">
          {/*
            The two groups wrap onto separate rows on a narrow screen rather
            than being squeezed side by side. The brand and the navigation
            stay together; the signed-in name and the sign-out button move
            below them.
          */}
          <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
            <Link to="/admin" className="flex items-center gap-3 rounded-lg">
              <BrandMark />
              <span className="pill bg-slate-900 text-white">Admin</span>
            </Link>
            <nav aria-label="Admin" className="flex items-center gap-1">
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
            {admin && (
              <div className="hidden items-center gap-2.5 sm:flex">
                <span
                  aria-hidden="true"
                  className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-sm font-bold text-slate-700"
                >
                  {admin.displayName.charAt(0).toUpperCase()}
                </span>
                <span className="text-sm text-slate-600">
                  Signed in as{' '}
                  <span className="font-semibold text-slate-900">
                    {admin.displayName}
                  </span>
                </span>
              </div>
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
        `tabIndex={-1}` makes the skip link move the focus, not just the
        scroll position: a fragment link on an element that cannot take the
        focus leaves a keyboard user still at the top of the page, with no
        idea where they now are.
      */}
      <main
        id="main"
        tabIndex={-1}
        className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 focus:outline-none"
      >
        <Outlet />
      </main>

      <footer className="border-t border-slate-200/80 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6">
          <p className="text-sm text-slate-500">
            LoveProof Admin — moderation is recorded in the audit log.
          </p>
        </div>
      </footer>
    </div>
  )
}
