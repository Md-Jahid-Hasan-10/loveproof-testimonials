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

/* ------------------------------------------------------------------ *
 * The customer surface.
 * ------------------------------------------------------------------ */

/**
 * The plan an account is on.
 *
 * The plan only ever changes from a confirmed Stripe webhook, so the
 * interface reads it rather than assuming it.
 */
export type Plan = 'FREE' | 'PREMIUM'

/**
 * The signed-in customer.
 *
 * Mirrors `UserResponse` in the backend.
 */
export interface Customer {
  id: string
  email: string
  displayName: string
  emailVerified: boolean
  plan: Plan
  cancelAtPeriodEnd: boolean
  subscriptionPastDue: boolean
  currentPeriodEnd: string | null
}

/**
 * A Space.
 *
 * Mirrors `SpaceResponse` in the backend.
 */
export interface Space {
  id: string
  title: string
  subtitle: string | null
  slug: string
  publicUrl: string
  fieldCompany: boolean
  fieldCompanyRequired: boolean
  fieldSocial: boolean
  fieldSocialRequired: boolean
  fieldWebsite: boolean
  fieldWebsiteRequired: boolean
  fieldJobTitle: boolean
  fieldJobTitleRequired: boolean
  ratingEnabled: boolean
  theme: string
  /** Whether the public URL can still change. */
  slugLocked: boolean
  /** Whether the owner may change it. A downgraded account has read-only Spaces. */
  editable: boolean
  createdAt: string
  updatedAt: string
}

/**
 * One of the themes a Space can use.
 */
export interface Theme {
  key: string
  label: string
  /** Whether the theme needs a Premium plan. */
  premium: boolean
}

/**
 * The request to create a Space.
 */
export interface CreateSpacePayload {
  title: string
  subtitle?: string | null
  slug?: string | null
  theme?: string | null
}

/**
 * The request to update a Space.
 *
 * Every field is optional and a missing field means "leave this alone", so a
 * partial update never resets the fields it did not mention.
 */
export interface UpdateSpacePayload {
  title?: string
  subtitle?: string | null
  slug?: string | null
  theme?: string | null
  ratingEnabled?: boolean
  fieldCompany?: boolean
  fieldCompanyRequired?: boolean
  fieldSocial?: boolean
  fieldSocialRequired?: boolean
  fieldWebsite?: boolean
  fieldWebsiteRequired?: boolean
  fieldJobTitle?: boolean
  fieldJobTitleRequired?: boolean
}
