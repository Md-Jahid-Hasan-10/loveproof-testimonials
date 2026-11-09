/**
 * The LoveProof wordmark.
 *
 * A single shared component so the brand looks the same in the customer
 * shell, the public shell and the admin shell. The mark is an SVG rather
 * than a glyph so it inherits the current text colour and scales cleanly.
 */
export function BrandMark({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <span
        aria-hidden="true"
        className="flex h-8 w-8 shrink-0 items-center justify-center rounded-[0.625rem] bg-gradient-to-br from-brand-500 to-brand-700 shadow-brand"
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="white"
          strokeWidth="2.25"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-[1.125rem] w-[1.125rem]"
        >
          <path d="M20.5 13.5c0 3.6-3.8 6.5-8.5 6.5a10 10 0 0 1-2.6-.34L4 21l1.3-3.7A6.2 6.2 0 0 1 3.5 13.5C3.5 9.9 7.3 7 12 7s8.5 2.9 8.5 6.5Z" />
          <path d="M9 12.2h6M9 15h3.5" />
        </svg>
      </span>
      <span className="text-[1.0625rem] font-bold tracking-tight text-slate-900">
        LoveProof
      </span>
    </span>
  )
}
