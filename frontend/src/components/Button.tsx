import type { ButtonHTMLAttributes, ReactNode } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
  children: ReactNode
}

/*
  The four variants, each with a hover, an active and a disabled state.

  The primary button carries a brand-tinted shadow rather than a flat fill.
  It is the one element on a screen that is meant to be pressed, and the
  shadow is what makes it read as pressable rather than as a coloured box.
*/
const VARIANT_CLASSES: Record<Variant, string> = {
  primary:
    'bg-brand-600 text-white shadow-brand hover:bg-brand-700 active:bg-brand-800 ' +
    'disabled:bg-brand-300 disabled:shadow-none',
  secondary:
    'border border-slate-300 bg-white text-slate-700 shadow-sm ' +
    'hover:border-slate-400 hover:bg-slate-50 hover:text-slate-900 ' +
    'active:bg-slate-100 disabled:text-slate-400 disabled:shadow-none',
  ghost:
    'bg-transparent text-brand-700 hover:bg-brand-50 hover:text-brand-800 ' +
    'active:bg-brand-100 disabled:text-slate-400',
  danger:
    'border border-rose-200 bg-white text-rose-700 shadow-sm ' +
    'hover:border-rose-300 hover:bg-rose-50 hover:text-rose-800 ' +
    'active:bg-rose-100 disabled:text-rose-300 disabled:shadow-none',
}

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'gap-1.5 px-3 py-1.5 text-[0.8125rem]',
  md: 'gap-2 px-4 py-2.5 text-sm',
}

/**
 * The shared button.
 *
 * While `loading` is set the button is disabled and announces its busy state
 * so a screen-reader user is not left guessing while the request is in flight.
 */
export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  type = 'button',
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center rounded-control font-semibold transition
        disabled:cursor-not-allowed ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${className}`}
      {...rest}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  )
}
