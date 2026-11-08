/**
 * The API types mirroring the backend contract.
 *
 * The shapes here are the ones produced by the Java records in
 * `backend/src/main/java/com/loveproof/testimonials/web/dto/`.
 */

export type TestimonialStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface Testimonial {
  id: string
  name: string
  role: string | null
  company: string | null
  rating: number
  content: string
  status: TestimonialStatus
  consentTextVersion: string
  submittedAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface PublicStats {
  count: number
  averageRating: number | null
}

export interface CreateTestimonialPayload {
  name: string
  role: string | null
  company: string | null
  rating: number
  content: string
  consent: boolean
}

/**
 * The shared error envelope returned by every failing endpoint (FR-6.1).
 */
export interface ErrorEnvelope {
  code: string
  message: string
  fieldErrors: Record<string, string>
  timestamp: string
  path: string
}

/**
 * The signed-in administrator.
 *
 * Returned by the session endpoints. The role is always `ADMIN` today, but it
 * travels with the session so the UI can tell a moderator from an owner
 * without another request.
 */
export interface AdminSession {
  username: string
  displayName: string
  role: string
  lastLoginAt: string | null
}

/**
 * The dashboard counts.
 *
 * Mirrors `DashboardStatsResponse` in the backend.
 */
export interface DashboardStats {
  total: number
  pending: number
  approved: number
  rejected: number
  deleted: number
  last7Days: number
  averageRating: number | null
}

/**
 * The status filter for the moderation inbox.
 *
 * `DELETED` is not a status — a deleted testimonial keeps the status it had
 * when it was deleted — so it is a separate view rather than a filter value.
 */
export type InboxFilter = TestimonialStatus | 'ALL'
