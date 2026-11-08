import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
}

/** A bordered surface used for the form and the result states. */
export function Card({ children, className = '' }: CardProps) {
  return (
    <div className={`rounded-xl border border-slate-200 bg-white shadow-sm ${className}`}>
      {children}
    </div>
  )
}
