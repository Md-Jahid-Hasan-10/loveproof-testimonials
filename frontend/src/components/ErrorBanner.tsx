interface ErrorBannerProps {
  title?: string
  message: string
  /** Rendered as a retry action, when the failure is retryable. */
  onRetry?: () => void
  onDismiss?: () => void
  retryLabel?: string
}

/**
 * A dismissible, retryable error banner.
 *
 * Server and network failures are surfaced here rather than as a blank screen
 * or an unhandled rejection (FR-6.3).
 */
export function ErrorBanner({
  title = 'Something went wrong',
  message,
  onRetry,
  onDismiss,
  retryLabel = 'Try again',
}: ErrorBannerProps) {
  return (
    <div
      role="alert"
      className="flex flex-col gap-3 rounded-card border border-rose-200 bg-rose-50/80 p-4 backdrop-blur sm:flex-row sm:items-start sm:justify-between"
    >
      <div className="flex gap-3">
        <span
          aria-hidden="true"
          className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-rose-100 text-sm font-bold text-rose-600"
        >
          !
        </span>
        <div className="flex flex-col gap-1">
          <p className="text-sm font-semibold text-rose-900">{title}</p>
          <p className="text-sm leading-relaxed text-rose-800">{message}</p>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-2 pl-9 sm:pl-0">
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="rounded-control border border-rose-300 bg-white px-3 py-1.5 text-sm font-semibold text-rose-900 shadow-sm transition hover:bg-rose-100"
          >
            {retryLabel}
          </button>
        )}
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss error"
            className="rounded-control px-2 py-1.5 text-sm font-medium text-rose-700 transition hover:bg-rose-100"
          >
            Dismiss
          </button>
        )}
      </div>
    </div>
  )
}
