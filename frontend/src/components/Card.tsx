import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
  /**
   * How much the card is lifted off the page.
   *
   * `flat` sits flush, `raised` is the default for a panel, and `floating`
   * is for a card that a person is meant to reach for first.
   */
  elevation?: 'flat' | 'raised' | 'floating'
}

/** The bordered surface used for the form, the result and the panel states. */
export function Card({
  children,
  className = '',
  elevation = 'raised',
}: CardProps) {
  const elevationClass =
    elevation === 'flat'
      ? 'rounded-card border border-slate-200/80 bg-white'
      : elevation === 'floating'
        ? 'surface-raised'
        : 'surface'

  return (
    <div className={`rounded-card ${elevationClass} ${className}`}>{children}</div>
  )
}
