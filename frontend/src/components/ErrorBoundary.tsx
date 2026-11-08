import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  failed: boolean
}

/**
 * Catches a render error and shows a message instead of a blank page.
 *
 * React unmounts the whole tree when a component throws while rendering, which
 * leaves the visitor looking at an empty document with no way forward. This
 * boundary keeps the shell around the failure and offers a way back.
 *
 * The error is logged to the console for the developer; the message shown is
 * deliberately plain, because the details of a render failure mean nothing to
 * the visitor and can name internals.
 */
export class ErrorBoundary extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  state: ErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('A page failed to render.', error, info.componentStack)
  }

  render(): ReactNode {
    if (this.state.failed) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
          <main className="w-full max-w-md rounded-lg border border-rose-200 bg-white p-6 text-center shadow-sm">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              This page could not be shown
            </h1>
            <p className="mt-2 text-sm text-slate-600">
              Something went wrong while the page was being drawn. Reloading
              the page usually clears it.
            </p>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="mt-6 inline-flex items-center rounded-lg bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700"
            >
              Reload the page
            </button>
          </main>
        </div>
      )
    }

    return this.props.children
  }
}
