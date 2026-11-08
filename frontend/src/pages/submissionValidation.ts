/**
 * The client-side validation rules.
 *
 * These mirror the server rules in `CreateTestimonialRequest` exactly. The
 * server remains authoritative (FR-1.11) — validating here is for fast
 * feedback, not for trusting the payload.
 *
 * The messages match the server's messages so a user never sees one wording
 * on the client and a different one after a round trip.
 */

export const NAME_MIN = 2
export const NAME_MAX = 100
export const ROLE_MAX = 120
export const COMPANY_MAX = 120
export const CONTENT_MIN = 20
export const CONTENT_MAX = 2000

export interface SubmissionFormValues {
  name: string
  role: string
  company: string
  rating: number | null
  content: string
  consent: boolean
}

export type SubmissionFieldErrors = Partial<Record<keyof SubmissionFormValues, string>>

/** The empty form. */
export const EMPTY_FORM: SubmissionFormValues = {
  name: '',
  role: '',
  company: '',
  rating: null,
  content: '',
  consent: false,
}

/**
 * Validates the form.
 *
 * @param values the current form values
 * @returns the per-field messages; empty when the form is valid
 */
export function validateSubmission(
  values: SubmissionFormValues,
): SubmissionFieldErrors {
  const errors: SubmissionFieldErrors = {}

  const name = values.name.trim()
  if (name.length === 0) {
    errors.name = 'Name is required.'
  } else if (name.length < NAME_MIN || name.length > NAME_MAX) {
    errors.name = `Name must be between ${NAME_MIN} and ${NAME_MAX} characters.`
  }

  const role = values.role.trim()
  if (role.length > ROLE_MAX) {
    errors.role = `Role must be at most ${ROLE_MAX} characters.`
  }

  const company = values.company.trim()
  if (company.length > COMPANY_MAX) {
    errors.company = `Company must be at most ${COMPANY_MAX} characters.`
  }

  if (values.rating === null) {
    errors.rating = 'Rating is required.'
  } else if (values.rating < 1 || values.rating > 5) {
    errors.rating = 'Rating must be between 1 and 5.'
  }

  const content = values.content.trim()
  if (content.length === 0) {
    errors.content = 'Testimonial text is required.'
  } else if (content.length < CONTENT_MIN || content.length > CONTENT_MAX) {
    errors.content = `Testimonial text must be between ${CONTENT_MIN} and ${CONTENT_MAX} characters.`
  }

  if (!values.consent) {
    errors.consent = 'You must consent to the publication of your testimonial.'
  }

  return errors
}
