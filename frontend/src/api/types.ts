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
 * The subscription status.
 *
 * The values are Stripe's own vocabulary, lower-cased, so the interface can
 * show what Stripe reported without a second translation table. `NONE` means
 * the account has never subscribed.
 */
export type SubscriptionStatus =
  | 'NONE'
  | 'active'
  | 'trialing'
  | 'past_due'
  | 'canceled'
  | 'unpaid'
  | 'incomplete'
  | 'incomplete_expired'
  | 'paused'

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
  /**
   * Whether the account has a password to change. A Google-only account has
   * none, so the settings page offers no password form for it.
   */
  hasPassword: boolean
  plan: Plan
  subscriptionStatus: SubscriptionStatus
  cancelAtPeriodEnd: boolean
  subscriptionPastDue: boolean
  currentPeriodEnd: string | null
}

/**
 * One of the plans, with its features and its limits.
 *
 * Mirrors `PlanOption` in the backend. The limits are read from the server
 * rather than hard-coded here, so the interface cannot drift from the rule
 * the server actually enforces.
 */
export interface PlanOption {
  plan: Plan
  label: string
  price: string
  spaces: number
  testimonialsPerSpace: number
  features: string[]
}

/**
 * The signed-in account's billing state.
 *
 * Mirrors `BillingResponse` in the backend.
 */
export interface Billing {
  plan: Plan
  subscriptionStatus: SubscriptionStatus
  cancelAtPeriodEnd: boolean
  pastDue: boolean
  currentPeriodEnd: string | null
  /** Whether this installation has Stripe configured at all. */
  billingConfigured: boolean
  plans: PlanOption[]
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

/**
 * A Space as the public form sees it.
 *
 * Mirrors `PublicSpaceResponse` in the backend. It carries no owner identity
 * and no editable flags: a visitor learns what to fill in and nothing about
 * the account that owns the Space.
 */
export interface PublicSpace {
  slug: string
  title: string
  subtitle: string | null
  theme: string
  fieldCompany: boolean
  fieldCompanyRequired: boolean
  fieldSocial: boolean
  fieldSocialRequired: boolean
  fieldWebsite: boolean
  fieldWebsiteRequired: boolean
  fieldJobTitle: boolean
  fieldJobTitleRequired: boolean
  ratingEnabled: boolean
  /** Whether the Space is still collecting submissions. */
  accepting: boolean
}

/**
 * The request to submit a testimonial into a Space.
 *
 * The optional fields are only sent when the Space asks for them; the server
 * decides which of them are required.
 */
export interface SubmitSpaceTestimonialPayload {
  name: string
  email: string
  content: string
  rating?: number | null
  company?: string | null
  socialLink?: string | null
  website?: string | null
  jobTitle?: string | null
  consent?: boolean
  /** The honeypot field. It is always empty for a real submission. */
  website2?: string
}

/**
 * The answer to a submission.
 *
 * The submitter's email is never included: the response identifies the
 * testimonial, not the person behind it.
 */
export interface SubmittedTestimonial {
  id: string | null
  submittedAt: string
}

/**
 * One testimonial in the owner's inbox.
 *
 * Mirrors `SpaceTestimonialResponse` in the backend. Unlike the public
 * response it carries the email: the owner needs it to recognise a
 * submission, and it is the owner's own data (PRD §4.7).
 */
export interface SpaceTestimonial {
  id: string
  name: string
  email: string
  content: string
  rating: number | null
  company: string | null
  socialLink: string | null
  website: string | null
  jobTitle: string | null
  consentGiven: boolean
  favorite: boolean
  wallOfLove: boolean
  visible: boolean
  submittedAt: string
}

/**
 * The curation flags a request may change.
 *
 * A missing flag means "leave this alone", so a request that only favourites
 * a testimonial never resets its Wall of Love or visibility state.
 */
export interface CurationPayload {
  favorite?: boolean
  wallOfLove?: boolean
  visible?: boolean
}

/**
 * A Space's embed configuration.
 *
 * Mirrors `EmbedConfigResponse` in the backend.
 */
export interface EmbedConfig {
  spaceId: string
  embedToken: string
  layout: EmbedLayout
  themeMode: EmbedThemeMode
  backgroundColor: string | null
  animation: boolean
  /** Where the embed script is served from. */
  scriptUrl: string
  /** Where the embed fetches its data from. */
  dataUrl: string
}

/**
 * The layout an embed renders in.
 *
 * The carousel needs a Premium plan.
 */
export type EmbedLayout = 'masonry' | 'carousel'

/**
 * Whether an embed renders light or dark.
 */
export type EmbedThemeMode = 'light' | 'dark'

/**
 * One embed option, with whether it needs a Premium plan.
 */
export interface EmbedOption {
  key: string
  label: string
  premium: boolean
}

/**
 * The request to save an embed configuration.
 *
 * Every field is optional and a missing field means "leave this alone".
 */
export interface UpdateEmbedConfigPayload {
  layout?: EmbedLayout
  themeMode?: EmbedThemeMode
  backgroundColor?: string | null
  animation?: boolean
}

/**
 * One testimonial as the embed sees it.
 *
 * There is no email: the embed ends up on somebody else's website.
 */
export interface EmbedTestimonial {
  name: string
  content: string
  rating: number | null
  company: string | null
  jobTitle: string | null
  website: string | null
  socialLink: string | null
}

/**
 * The public data an embed renders.
 *
 * Mirrors `PublicEmbedResponse` in the backend.
 */
export interface PublicEmbed {
  title: string
  subtitle: string | null
  layout: EmbedLayout
  themeMode: EmbedThemeMode
  backgroundColor: string | null
  animation: boolean
  testimonials: EmbedTestimonial[]
}
