import { Link, NavLink, Outlet } from 'react-router-dom'

/**
 * The public shell: a skip link, a header with the brand and the public
 * navigation, and a footer.
 *
 * The skip link is the first focusable element on the page so a keyboard user
 * can jump straight past the navigation to the form.
 */
export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to content
      </a>

      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-4 py-4">
          <Link to="/" className="text-lg font-bold tracking-tight text-slate-900">
            LoveProof
          </Link>
          <nav aria-label="Main" className="flex items-center gap-1">
            <NavLink
              to="/wall"
              className={({ isActive }) =>
                `rounded-lg px-3 py-2 text-sm font-medium ${
                  isActive
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`
              }
            >
              Wall
            </NavLink>
            <NavLink
              to="/submit"
              className={({ isActive }) =>
                `rounded-lg px-3 py-2 text-sm font-medium ${
                  isActive
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`
              }
            >
              Share your story
            </NavLink>
          </nav>
        </div>
      </header>

      <main id="main" className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 sm:py-12">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-3xl px-4 py-6">
          <p className="text-sm text-slate-500">
            LoveProof — collect, moderate and publish customer testimonials.
          </p>
        </div>
      </footer>
    </div>
  )
}
