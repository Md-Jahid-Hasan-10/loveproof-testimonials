import type {
  AdminSession,
  CreateTestimonialPayload,
  DashboardStats,
  ErrorEnvelope,
  Page,
  PublicStats,
  Testimonial,
  TestimonialStatus,
} from './types'

/**
 * An API failure carrying the server's error envelope.
 *
 * The UI needs three different things from a failure: the machine-readable
 * code, the message, and the per-field messages that belong next to the
 * offending inputs (FR-6.1, FR-6.2). All three live on this error.
 */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly fieldErrors: Record<string, string>
  readonly path: string | undefined
  /** How long to wait before retrying, when the server said so. */
  readonly retryAfterSeconds: number | undefined

  constructor(
    status: number,
    envelope: Partial<ErrorEnvelope> | undefined,
    fallbackMessage: string,
    retryAfterSeconds?: number,
  ) {
    super(envelope?.message ?? fallbackMessage)
    this.name = 'ApiError'
    this.status = status
    this.code = envelope?.code ?? 'UNKNOWN_ERROR'
    this.fieldErrors = envelope?.fieldErrors ?? {}
    this.path = envelope?.path
    this.retryAfterSeconds = retryAfterSeconds
  }

  /** Whether the failure is a field-level validation failure. */
  get isValidationFailure(): boolean {
    return this.status === 400
  }

  /** Whether the failure is a refusal by the rate limiter. */
  get isRateLimited(): boolean {
    return this.status === 429
  }
}

/**
 * Reads the CSRF token from the readable cookie the backend issues.
 *
 * Spring Security's cookie CSRF repository writes the token to `XSRF-TOKEN`
 * and expects it back in the `X-XSRF-TOKEN` header. The token is readable by
 * JavaScript on purpose — that is how the SPA can echo it.
 */
function readCsrfToken(): string | undefined {
  if (typeof document === 'undefined') {
    return undefined
  }
  const match = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith('XSRF-TOKEN='))
  return match ? decodeURIComponent(match.slice('XSRF-TOKEN='.length)) : undefined
}

/**
 * Builds the request headers, adding the CSRF token to state-changing calls.
 */
function buildHeaders(method: string, hasBody: boolean): HeadersInit {
  const headers: Record<string, string> = {}
  if (hasBody) {
    headers['Content-Type'] = 'application/json'
  }
  if (method.toUpperCase() !== 'GET') {
    const token = readCsrfToken()
    if (token) {
      headers['X-XSRF-TOKEN'] = token
    }
  }
  return headers
}

/**
 * Reads the error envelope from a failed response.
 *
 * A failure may not carry the envelope at all — a proxy error page, a network
 * hiccup — so a non-JSON body degrades to an undefined envelope rather than
 * throwing.
 */
async function readErrorEnvelope(
  response: Response,
): Promise<Partial<ErrorEnvelope> | undefined> {
  try {
    const body = await response.json()
    return typeof body === 'object' && body !== null ? body : undefined
  } catch {
    return undefined
  }
}

/**
 * Reads the `Retry-After` header, when the server sent one.
 *
 * The header is in whole seconds. A value that is not a number is ignored: a
 * malformed header must not turn into a malformed failure.
 */
function readRetryAfter(response: Response): number | undefined {
  const header = response.headers?.get('Retry-After')
  if (header === null || header === undefined) {
    return undefined
  }
  const seconds = Number.parseInt(header, 10)
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined
}

/**
 * Performs a JSON request against the API.
 *
 * @param method the HTTP method
 * @param path the path, relative to the API root
 * @param body the request body, when there is one
 * @returns the parsed response body
 * @throws {ApiError} when the response is not successful
 */
async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    headers: buildHeaders(method, body !== undefined),
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const envelope = await readErrorEnvelope(response)
    throw new ApiError(
      response.status,
      envelope,
      `The request failed with status ${response.status}.`,
      readRetryAfter(response),
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

/** Submits a testimonial. The API stores it as `PENDING` (FR-1.10). */
export function createTestimonial(
  payload: CreateTestimonialPayload,
): Promise<Testimonial> {
  return request<Testimonial>('POST', '/testimonials', payload)
}

/** Lists the public wall: approved, non-deleted testimonials only. */
export function listTestimonials(params: {
  rating?: number
  sort?: 'submittedAt' | 'rating'
  page?: number
  size?: number
} = {}): Promise<Page<Testimonial>> {
  const query = new URLSearchParams()
  if (params.rating !== undefined) query.set('rating', String(params.rating))
  if (params.sort !== undefined) query.set('sort', params.sort)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const suffix = query.toString()
  return request<Page<Testimonial>>('GET', `/testimonials${suffix ? `?${suffix}` : ''}`)
}

/** Reads the public statistics: the approved count and the average rating. */
export function getPublicStats(): Promise<PublicStats> {
  return request<PublicStats>('GET', '/testimonials/stats')
}

/* ------------------------------------------------------------------ *
 * The admin surface.
 *
 * These calls ride on the session cookie. The client sends the CSRF
 * token automatically (see `buildHeaders`), so the caller never handles
 * it.
 * ------------------------------------------------------------------ */

/**
 * Signs an administrator in.
 *
 * A wrong username and a wrong password fail the same way, with the same
 * message, so the endpoint cannot be used to discover which accounts exist.
 */
export function signIn(username: string, password: string): Promise<AdminSession> {
  return request<AdminSession>('POST', '/admin/session', { username, password })
}

/**
 * Reads the current session, when there is one.
 *
 * Answers `401` when the visitor is not signed in, which is how the UI
 * restores the session after a page refresh.
 */
export function getSession(): Promise<AdminSession> {
  return request<AdminSession>('GET', '/admin/session')
}

/** Signs the current administrator out and invalidates the session server-side. */
export function signOut(): Promise<void> {
  return request<void>('DELETE', '/admin/session')
}

/** Reads the dashboard counts. */
export function getDashboardStats(): Promise<DashboardStats> {
  return request<DashboardStats>('GET', '/admin/testimonials/stats')
}

/**
 * Lists the moderation inbox.
 *
 * @param params the status filter, the free-text search and the page
 */
export function listInbox(params: {
  status?: TestimonialStatus
  q?: string
  page?: number
  size?: number
} = {}): Promise<Page<Testimonial>> {
  const query = new URLSearchParams()
  if (params.status !== undefined) query.set('status', params.status)
  if (params.q !== undefined && params.q !== '') query.set('q', params.q)
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  const suffix = query.toString()
  return request<Page<Testimonial>>(
    'GET',
    `/admin/testimonials${suffix ? `?${suffix}` : ''}`,
  )
}

/**
 * Approves a testimonial, making it immediately visible on the public wall.
 *
 * @param id the testimonial identifier
 */
export function approveTestimonial(id: string): Promise<Testimonial> {
  return request<Testimonial>('POST', `/admin/testimonials/${id}/approve`)
}

/**
 * Rejects a testimonial with a reason.
 *
 * @param id     the testimonial identifier
 * @param reason why the testimonial was rejected
 */
export function rejectTestimonial(
  id: string,
  reason: string,
): Promise<Testimonial> {
  return request<Testimonial>('POST', `/admin/testimonials/${id}/reject`, {
    reason,
  })
}

/**
 * Soft-deletes a testimonial.
 *
 * @param id the testimonial identifier
 */
export function deleteTestimonial(id: string): Promise<Testimonial> {
  return request<Testimonial>('DELETE', `/admin/testimonials/${id}`)
}

/**
 * Restores a soft-deleted testimonial.
 *
 * @param id the testimonial identifier
 */
export function restoreTestimonial(id: string): Promise<Testimonial> {
  return request<Testimonial>('POST', `/admin/testimonials/${id}/restore`)
}
