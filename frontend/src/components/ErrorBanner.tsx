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
      className="flex flex-col gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 sm:flex-row sm:items-start sm:justify-between"
    >
      <div className="flex flex-col gap-1">
        <p className="text-sm font-semibold text-rose-900">{title}</p>
        <p className="text-sm text-rose-800">{message}</p>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-rose-900 ring-1 ring-inset ring-rose-300 hover:bg-rose-100"
          >
            {retryLabel}
          </button>
        )}
        {onDismiss && (
          <button
            type="button"
            onClick={onDismiss}
            aria-label="Dismiss error"
            className="rounded-lg px-2 py-1.5 text-sm font-medium text-rose-800 hover:bg-rose-100"
          >
            Dismiss
          </button>
        )}
      </div>
    </div>
  )
}
