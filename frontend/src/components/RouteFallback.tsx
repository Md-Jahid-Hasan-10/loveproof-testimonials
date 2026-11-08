/**
 * The placeholder shown while a route's code is being fetched.
 *
 * The routes are loaded lazily, so there is a short moment between the click
 * and the page appearing. Without this the screen would simply stay blank,
 * which reads as a broken page rather than as a page on its way.
 */
export function RouteFallback() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-screen items-center justify-center bg-slate-50"
    >
      <p className="text-sm text-slate-600">Loading…</p>
    </div>
  )
}
