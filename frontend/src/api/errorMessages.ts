import { ApiError } from './client'

/**
 * Turns a failure into a message a visitor can act on.
 *
 * The raw error is not always fit to show. A network failure carries a
 * technical message — "Failed to fetch" — that means nothing to the person
 * reading it. A rate-limit refusal carries the server's wording, which is
 * already written for a human. A session that has expired is a different
 * situation from a server that is down, and the two need different wording.
 *
 * Every screen shows its failures through this function so the phrasing is
 * the same everywhere.
 *
 * @param cause the caught failure
 * @param fallback what to say when the failure carries no usable wording
 * @param expiredSession whether a 401 means "your session ran out" and should
 *                        be reworded. The sign-in page leaves this off: its 401
 *                        is the deliberate "invalid username or password", and
 *                        replacing that would say more than the server meant.
 * @returns the message to show
 */
export function describeFailure(
  cause: unknown,
  fallback: string,
  expiredSession = true,
): string {
  if (cause instanceof ApiError) {
    if (expiredSession && cause.status === 401) {
      return 'Your session has expired. Sign in again to continue.'
    }
    if (cause.isRateLimited) {
      return cause.message
    }
    return cause.message || fallback
  }

  // A TypeError is what a failed `fetch` throws: the request never reached
  // the server, so the technical wording is replaced with plain language.
  if (cause instanceof TypeError) {
    return 'We could not reach the server. Please check your connection and try again.'
  }

  if (cause instanceof Error) {
    return cause.message || fallback
  }

  return fallback
}
