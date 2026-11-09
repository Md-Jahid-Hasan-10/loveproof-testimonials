import type { ReactNode } from 'react'

interface SpaceThemeProps {
  /** The theme key, as stored on the Space. */
  theme: string
  children: ReactNode
}

/**
 * The look of a Space's public page.
 *
 * <p>The theme changes the page's colours and shape, never what the form
 * asks for. The six themes come from the server's catalogue (PRD §4.4), so
 * this file only maps a key onto a set of classes.
 *
 * <p>An unknown key falls back to the basic theme. A Space whose theme the
 * server no longer offers still has to render: a missing theme must not take
 * the page down with it.
 */
export function SpaceTheme({ theme, children }: SpaceThemeProps) {
  const classes = themeClasses(theme)

  return (
    <div
      data-theme={theme}
      className={`${classes.page} min-h-screen`}
    >
      <div className={`${classes.panel} mx-auto max-w-2xl px-4 py-12 sm:px-6`}>
        {children}
      </div>
    </div>
  )
}

/**
 * The classes for one theme.
 */
function themeClasses(theme: string): { page: string; panel: string } {
  switch (theme) {
    case 'cards':
      return {
        page: 'bg-canvas',
        panel: 'rounded-card bg-white p-8 shadow-raised',
      }
    case 'minimal':
      return {
        page: 'bg-white',
        panel: 'max-w-xl',
      }
    case 'bold':
      return {
        page: 'bg-slate-900',
        panel: 'rounded-card bg-white p-8',
      }
    case 'gradient':
      return {
        page: 'bg-gradient-to-br from-brand-600 via-purple-600 to-pink-500',
        panel: 'rounded-card bg-white p-8 shadow-lifted',
      }
    case 'spotlight':
      return {
        page: 'bg-amber-50',
        panel: 'rounded-card border-2 border-amber-400 bg-white p-8 shadow-raised',
      }
    case 'basic':
    default:
      return {
        page: 'bg-canvas',
        panel: 'rounded-card bg-white p-8 shadow-card',
      }
  }
}
