import { Link, NavLink, Outlet } from 'react-router-dom'
import { BrandMark } from './BrandMark'

/**
 * The public shell: a skip link, a header with the brand and the public
 * navigation, and a footer.
 *
 * The skip link is the first focusable element on the page so a keyboard user
 * can jump straight past the navigation to the form.
 */
export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Skip to content
      </a>

      <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-white/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-x-4 gap-y-2 px-4 py-3.5">
          <Link to="/" className="rounded-lg">
            <BrandMark />
          </Link>
          <nav aria-label="Main" className="flex items-center gap-1">
            <NavLink
              to="/wall"
              className={({ isActive }) =>
                `nav-link ${isActive ? 'nav-link-active' : ''}`
              }
            >
              Wall
            </NavLink>
            <NavLink
              to="/submit"
              className={({ isActive }) =>
                `nav-link ${isActive ? 'nav-link-active' : ''}`
              }
            >
              Share your story
            </NavLink>
          </nav>
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
        className="mx-auto w-full max-w-3xl flex-1 px-4 py-8 focus:outline-none sm:py-12"
      >
        <Outlet />
      </main>

      <footer className="border-t border-slate-200/80 bg-white">
        <div className="mx-auto max-w-3xl px-4 py-6">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-2.5">
              <span
                aria-hidden="true"
                className="flex h-6 w-6 items-center justify-center rounded-[0.5rem] bg-gradient-to-br from-brand-500 to-brand-700"
              >
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="white"
                  strokeWidth="2.25"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="h-3.5 w-3.5"
                >
                  <path d="M20.5 13.5c0 3.6-3.8 6.5-8.5 6.5a10 10 0 0 1-2.6-.34L4 21l1.3-3.7A6.2 6.2 0 0 1 3.5 13.5C3.5 9.9 7.3 7 12 7s8.5 2.9 8.5 6.5Z" />
                </svg>
              </span>
              <p className="text-sm font-semibold text-slate-700">
                LoveProof
              </p>
            </div>
            <p className="text-sm text-slate-500">
              Collect, moderate and publish customer testimonials.
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}
