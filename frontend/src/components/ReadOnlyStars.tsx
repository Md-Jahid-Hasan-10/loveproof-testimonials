interface ReadOnlyStarsProps {
  /** The rating, 1-5. */
  rating: number
  /** How the rating is announced, e.g. "4 out of 5". */
  label?: string
  className?: string
}

/**
 * A read-only star rating.
 *
 * The stars are decorative and the value is carried by the text alternative, so
 * the rating is never conveyed by the glyph or its colour alone (FR-5.2). The
 * stars are `aria-hidden` and the text is exposed to a screen reader.
 */
export function ReadOnlyStars({
  rating,
  label,
  className = '',
}: ReadOnlyStarsProps) {
  const text = label ?? `${rating} out of 5`

  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      <span aria-hidden="true" className="text-sm leading-none text-amber-500">
        {'★'.repeat(rating)}
        <span className="text-slate-200">{'★'.repeat(5 - rating)}</span>
      </span>
      <span className="sr-only">{text}</span>
    </span>
  )
}
