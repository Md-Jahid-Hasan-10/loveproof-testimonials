import { useId } from 'react'
import type { ReactNode } from 'react'

interface FieldProps {
  label: string
  /** The field-level error message, when the field is invalid. */
  error?: string
  /** Extra guidance rendered under the label. */
  hint?: ReactNode
  required?: boolean
  children: (props: {
    id: string
    'aria-describedby': string | undefined
    'aria-invalid': boolean | undefined
  }) => ReactNode
}

/**
 * A labelled form field with its error and hint wired up for assistive tech.
 *
 * The render-prop shape keeps the `aria-*` attributes and the `id` on the same
 * control as the label, which a plain wrapper cannot guarantee (FR-6.2).
 */
export function Field({
  label,
  error,
  hint,
  required = false,
  children,
}: FieldProps) {
  const id = useId()
  const errorId = `${id}-error`
  const hintId = `${id}-hint`
  const describedBy =
    [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') ||
    undefined

  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor={id}
        className="text-sm font-semibold tracking-tight text-slate-800"
      >
        {label}
        {required && (
          <span className="ml-1 text-rose-500" aria-hidden="true">
            *
          </span>
        )}
        {required && <span className="sr-only"> (required)</span>}
      </label>
      {hint && (
        <p id={hintId} className="-mt-1 text-[0.8125rem] leading-relaxed text-slate-500">
          {hint}
        </p>
      )}
      {children({
        id,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
      })}
      {error && (
        <p id={errorId} className="flex items-start gap-1.5 text-sm font-medium text-rose-600">
          <span aria-hidden="true" className="mt-px shrink-0">
            ⚠
          </span>
          <span>{error}</span>
        </p>
      )}
    </div>
  )
}
