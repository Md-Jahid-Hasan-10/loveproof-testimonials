interface LoadingIndicatorProps {
  /** What is being loaded, e.g. "Loading testimonials…". */
  label: string
  /** Whether the indicator is shown. */
  loading: boolean
  /**
   * Whether the region already holds content.
   *
   * The first load replaces an empty region, so it shows a plain message. A
   * refresh over content that is already on screen shows a smaller marker
   * instead, so the visitor keeps reading the old rows while the new ones are
   * on their way.
   */
  hasContent?: boolean
}

/**
 * The loading state of a region of the page.
 *
 * The region is marked `aria-busy` so a screen-reader user is told that the
 * content is being updated rather than being told that the page ended.
 */
export function LoadingIndicator({
  label,
  loading,
  hasContent = false,
}: LoadingIndicatorProps) {
  if (!loading) {
    return null
  }

  if (hasContent) {
    return (
      <p
        role="status"
        aria-live="polite"
        className="flex items-center gap-2 text-sm text-slate-600"
      >
        <span
          aria-hidden="true"
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-slate-400 border-t-transparent"
        />
        {label}
      </p>
    )
  }

  return (
    <p role="status" aria-live="polite" className="text-sm text-slate-600">
      {label}
    </p>
  )
}
