import type { EmbedLayout, EmbedThemeMode, EmbedTestimonial } from '../api'

interface EmbedPreviewProps {
  title: string
  subtitle: string | null
  layout: EmbedLayout
  themeMode: EmbedThemeMode
  backgroundColor: string | null
  animation: boolean
  testimonials: EmbedTestimonial[]
}

/**
 * A preview of the embed, drawn the way the embed script draws it.
 *
 * <p>The preview and the real widget are two implementations of the same
 * idea, so they are kept close together: the same class names, the same
 * layout rules, the same order. What the owner sees here is what the host
 * page gets, apart from the testimonials themselves.
 */
export function EmbedPreview({
  title,
  subtitle,
  layout,
  themeMode,
  backgroundColor,
  animation,
  testimonials,
}: EmbedPreviewProps) {
  const dark = themeMode === 'dark'

  return (
    <div
      className={`rounded-xl border p-5 ${dark ? 'border-slate-700 bg-slate-900' : 'border-slate-200 bg-white'}`}
      style={
        backgroundColor
          ? { backgroundColor }
          : undefined
      }
    >
      {title && (
        <p
          className={`text-lg font-bold ${dark ? 'text-slate-100' : 'text-slate-900'}`}
        >
          {title}
        </p>
      )}

      {subtitle && (
        <p
          className={`mt-0.5 text-sm ${dark ? 'text-slate-400' : 'text-slate-600'}`}
        >
          {subtitle}
        </p>
      )}

      {testimonials.length === 0 ? (
        <p
          className={`mt-4 text-sm ${dark ? 'text-slate-400' : 'text-slate-500'}`}
        >
          No testimonials yet. Add some to your Wall of Love and they will
          appear here.
        </p>
      ) : layout === 'carousel' ? (
        <div className="mt-4 flex gap-4 overflow-x-auto pb-2">
          {testimonials.map((testimonial, index) => (
            <EmbedPreviewCard
              key={index}
              testimonial={testimonial}
              dark={dark}
              className="w-72 shrink-0"
            />
          ))}
        </div>
      ) : (
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          {testimonials.map((testimonial, index) => (
            <EmbedPreviewCard key={index} testimonial={testimonial} dark={dark} />
          ))}
        </div>
      )}

      {animation && testimonials.length > 0 && (
        <p className="mt-4 text-xs text-slate-400">
          The testimonials fade in when the page loads.
        </p>
      )}
    </div>
  )
}

interface EmbedPreviewCardProps {
  testimonial: EmbedTestimonial
  dark: boolean
  className?: string
}

/**
 * One testimonial in the preview.
 */
function EmbedPreviewCard({
  testimonial,
  dark,
  className = '',
}: EmbedPreviewCardProps) {
  const meta = [testimonial.jobTitle, testimonial.company]
    .filter((part): part is string => Boolean(part))
    .join(' · ')

  return (
    <article
      className={`rounded-lg border p-4 ${
        dark
          ? 'border-slate-700 bg-slate-800'
          : 'border-slate-200 bg-slate-50'
      } ${className}`}
    >
      <p
        className={`text-sm font-semibold ${dark ? 'text-slate-100' : 'text-slate-900'}`}
      >
        {testimonial.name}
      </p>

      {meta && (
        <p
          className={`mt-0.5 text-xs ${dark ? 'text-slate-400' : 'text-slate-500'}`}
        >
          {meta}
        </p>
      )}

      {testimonial.rating !== null && testimonial.rating > 0 && (
        <p aria-hidden="true" className="mt-1 text-sm text-amber-500">
          {'★'.repeat(testimonial.rating)}
          <span className="text-slate-300">
            {'★'.repeat(5 - testimonial.rating)}
          </span>
        </p>
      )}

      <p
        className={`mt-2 whitespace-pre-wrap text-sm ${dark ? 'text-slate-300' : 'text-slate-700'}`}
      >
        {testimonial.content}
      </p>
    </article>
  )
}
