import type { PublicSpace } from '../api'

/**
 * The client-side validation rules for a Space submission.
 *
 * These mirror the server rules in `SubmitSpaceTestimonialRequest` and
 * `SpaceSubmissionService`. The server remains authoritative (PRD §4.5) —
 * validating here is for fast feedback, not for trusting the payload.
 *
 * Which fields are required depends on the Space's configuration, so the
 * Space itself is part of the input: the same rules cannot be written once
 * for every Space.
 */

export const NAME_MAX = 100
export const EMAIL_MAX = 254
export const CONTENT_MAX = 2000
export const COMPANY_MAX = 120
export const SOCIAL_MAX = 500
export const WEBSITE_MAX = 500
export const JOB_TITLE_MAX = 120

export interface SpaceSubmissionValues {
  name: string
  email: string
  content: string
  rating: number | null
  company: string
  socialLink: string
  website: string
  jobTitle: string
  consent: boolean
}

export type SpaceSubmissionFieldErrors = Partial<
  Record<keyof SpaceSubmissionValues, string>
>

/** The empty form. */
export const EMPTY_SPACE_SUBMISSION: SpaceSubmissionValues = {
  name: '',
  email: '',
  content: '',
  rating: null,
  company: '',
  socialLink: '',
  website: '',
  jobTitle: '',
  consent: false,
}

/**
 * A deliberately simple email check.
 *
 * The server uses Jakarta Validation's `@Email`, which is looser than this.
 * This is stricter on purpose: it is better to ask for the address again
 * than to accept a typo and lose the submission. The server still has the
 * final say.
 */
function looksLikeEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
}

/**
 * Checks that a link is an absolute URL.
 *
 * The PRD requires a URL format check for the website and social fields
 * (PRD §4.5). A link that cannot be opened is worse than no link, so the
 * form asks for one that can.
 */
function looksLikeUrl(value: string): boolean {
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

/**
 * Validates the form against the Space's configuration.
 *
 * @param values the current form values
 * @param space  the Space the submission is going to
 * @returns the per-field messages; empty when the form is valid
 */
export function validateSpaceSubmission(
  values: SpaceSubmissionValues,
  space: PublicSpace,
): SpaceSubmissionFieldErrors {
  const errors: SpaceSubmissionFieldErrors = {}

  const name = values.name.trim()
  if (name.length === 0) {
    errors.name = 'Name is required.'
  } else if (name.length > NAME_MAX) {
    errors.name = `Name must be at most ${NAME_MAX} characters.`
  }

  const email = values.email.trim()
  if (email.length === 0) {
    errors.email = 'Email is required.'
  } else if (email.length > EMAIL_MAX) {
    errors.email = `Email must be at most ${EMAIL_MAX} characters.`
  } else if (!looksLikeEmail(email)) {
    errors.email = 'Email must be a valid email address.'
  }

  const content = values.content.trim()
  if (content.length === 0) {
    errors.content = 'Testimonial text is required.'
  } else if (content.length > CONTENT_MAX) {
    errors.content = `Testimonial text must be at most ${CONTENT_MAX} characters.`
  }

  if (space.ratingEnabled) {
    if (values.rating === null) {
      errors.rating = 'Rating is required.'
    } else if (values.rating < 1 || values.rating > 5) {
      errors.rating = 'Rating must be between 1 and 5.'
    }
  }

  if (space.fieldCompany) {
    const company = values.company.trim()
    if (company.length > COMPANY_MAX) {
      errors.company = `Company name must be at most ${COMPANY_MAX} characters.`
    } else if (space.fieldCompanyRequired && company.length === 0) {
      errors.company = 'Company name is required.'
    }
  }

  if (space.fieldSocial) {
    const social = values.socialLink.trim()
    if (social.length > SOCIAL_MAX) {
      errors.socialLink = `Social media link must be at most ${SOCIAL_MAX} characters.`
    } else if (social.length > 0 && !looksLikeUrl(social)) {
      errors.socialLink = 'Social media link must be a full URL.'
    } else if (space.fieldSocialRequired && social.length === 0) {
      errors.socialLink = 'Social media links are required.'
    }
  }

  if (space.fieldWebsite) {
    const website = values.website.trim()
    if (website.length > WEBSITE_MAX) {
      errors.website = `Website URL must be at most ${WEBSITE_MAX} characters.`
    } else if (website.length > 0 && !looksLikeUrl(website)) {
      errors.website = 'Website URL must be a full URL.'
    } else if (space.fieldWebsiteRequired && website.length === 0) {
      errors.website = 'Website URL is required.'
    }
  }

  if (space.fieldJobTitle) {
    const jobTitle = values.jobTitle.trim()
    if (jobTitle.length > JOB_TITLE_MAX) {
      errors.jobTitle = `Job title must be at most ${JOB_TITLE_MAX} characters.`
    } else if (space.fieldJobTitleRequired && jobTitle.length === 0) {
      errors.jobTitle = 'Job title is required.'
    }
  }

  return errors
}
