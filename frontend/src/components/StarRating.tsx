import { useId } from 'react'

interface StarRatingProps {
  /** The chosen rating, 1-5. `null` when nothing is chosen yet. */
  value: number | null
  onChange: (rating: number) => void
  /** The field-level error message, when the field is invalid. */
  error?: string
  disabled?: boolean
}

/**
 * The 1-5 star rating control.
 *
 * Implemented as a radio group rather than as buttons, so the value is part of
 * the form semantics: a screen reader announces "Rating, radio group", the
 * arrow keys move between the stars, and the whole control is one tab stop.
 *
 * Each star carries its own text label ("3 out of 5"), so the control is
 * operable and understandable without relying on the glyph or its colour.
 */
export function StarRating({
  value,
  onChange,
  error,
  disabled = false,
}: StarRatingProps) {
  const id = useId()
  const errorId = `${id}-error`

  return (
    <div
      role="radiogroup"
      aria-label="Rating"
      aria-describedby={error ? errorId : undefined}
      aria-invalid={error ? true : undefined}
      className="flex flex-col gap-1.5"
    >
      <div className="flex items-center gap-1">
        {[1, 2, 3, 4, 5].map((star) => {
          const filled = value !== null && star <= value
          return (
            <label key={star} className="cursor-pointer p-0.5">
              <input
                type="radio"
                name={`${id}-rating`}
                value={star}
                checked={value === star}
                disabled={disabled}
                onChange={() => onChange(star)}
                className="peer sr-only"
              />
              <span
                aria-hidden="true"
                className={`block text-3xl leading-none transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-brand-500 peer-focus-visible:ring-offset-2 ${filled ? 'text-amber-400' : 'text-slate-300'} ${disabled ? 'cursor-not-allowed' : ''}`}
              >
                ★
              </span>
              <span className="sr-only">
                {star} out of 5{star === 1 ? ' star' : ' stars'}
              </span>
            </label>
          )
        })}
        <span className="ml-2 text-sm text-slate-600">
          {value === null
            ? 'No rating selected'
            : `${value} out of 5${value === 1 ? ' star' : ' stars'}`}
        </span>
      </div>
      {error && (
        <p id={errorId} className="text-sm font-medium text-rose-600">
          {error}
        </p>
      )}
    </div>
  )
}
