import { describe, expect, it } from 'vitest'
import type { PublicSpace } from '../api'
import {
  EMPTY_SPACE_SUBMISSION,
  validateSpaceSubmission,
} from './spaceSubmissionValidation'

/**
 * The validation rules for a Space submission.
 *
 * <p>Which fields are required depends on the Space's configuration, so these
 * tests are mostly about the configuration being honoured rather than about
 * the limits themselves.
 */

const SPACE: PublicSpace = {
  slug: 'customer-reviews',
  title: 'Customer reviews',
  subtitle: null,
  theme: 'basic',
  fieldCompany: false,
  fieldCompanyRequired: false,
  fieldSocial: false,
  fieldSocialRequired: false,
  fieldWebsite: false,
  fieldWebsiteRequired: false,
  fieldJobTitle: false,
  fieldJobTitleRequired: false,
  ratingEnabled: false,
  accepting: true,
}

/** A complete, valid submission. */
const VALID = {
  ...EMPTY_SPACE_SUBMISSION,
  name: 'Grace',
  email: 'grace@example.com',
  content: 'They did a wonderful job for us.',
}

describe('the Space submission rules', () => {
  it('accepts a complete submission', () => {
    expect(validateSpaceSubmission(VALID, SPACE)).toEqual({})
  })

  it('requires a name, an email and the testimonial text', () => {
    const errors = validateSpaceSubmission(EMPTY_SPACE_SUBMISSION, SPACE)

    expect(errors.name).toBe('Name is required.')
    expect(errors.email).toBe('Email is required.')
    expect(errors.content).toBe('Testimonial text is required.')
  })

  it('rejects an email that is not an address', () => {
    const errors = validateSpaceSubmission(
      { ...VALID, email: 'not-an-email' },
      SPACE,
    )

    expect(errors.email).toBe('Email must be a valid email address.')
  })

  it('does not require a rating when the Space has ratings turned off', () => {
    expect(validateSpaceSubmission(VALID, SPACE).rating).toBeUndefined()
  })

  it('requires a rating when the Space asks for one', () => {
    const errors = validateSpaceSubmission(
      VALID,
      { ...SPACE, ratingEnabled: true },
    )

    expect(errors.rating).toBe('Rating is required.')
  })

  it('does not require a company the Space does not ask for', () => {
    const errors = validateSpaceSubmission(VALID, SPACE)

    expect(errors.company).toBeUndefined()
  })

  it('requires a company the Space marks as required', () => {
    const errors = validateSpaceSubmission(
      VALID,
      { ...SPACE, fieldCompany: true, fieldCompanyRequired: true },
    )

    expect(errors.company).toBe('Company name is required.')
  })

  it('does not require a company the Space asks for but does not require', () => {
    const errors = validateSpaceSubmission(
      VALID,
      { ...SPACE, fieldCompany: true },
    )

    expect(errors.company).toBeUndefined()
  })

  it('requires a website the Space marks as required', () => {
    const errors = validateSpaceSubmission(
      VALID,
      { ...SPACE, fieldWebsite: true, fieldWebsiteRequired: true },
    )

    expect(errors.website).toBe('Website URL is required.')
  })

  it('requires a job title the Space marks as required', () => {
    const errors = validateSpaceSubmission(
      VALID,
      { ...SPACE, fieldJobTitle: true, fieldJobTitleRequired: true },
    )

    expect(errors.jobTitle).toBe('Job title is required.')
  })

  it('requires a social link the Space marks as required', () => {
    const errors = validateSpaceSubmission(
      VALID,
      { ...SPACE, fieldSocial: true, fieldSocialRequired: true },
    )

    expect(errors.socialLink).toBe('Social media links are required.')
  })

  it('asks for a full URL in a link field', () => {
    const errors = validateSpaceSubmission(
      { ...VALID, website: 'example.com' },
      { ...SPACE, fieldWebsite: true },
    )

    expect(errors.website).toBe('Website URL must be a full URL.')
  })

  it('accepts a full URL in a link field', () => {
    const errors = validateSpaceSubmission(
      { ...VALID, website: 'https://example.com' },
      { ...SPACE, fieldWebsite: true },
    )

    expect(errors.website).toBeUndefined()
  })

  it('rejects a URL with a scheme that cannot be opened', () => {
    const errors = validateSpaceSubmission(
      { ...VALID, website: 'javascript:alert(1)' },
      { ...SPACE, fieldWebsite: true },
    )

    expect(errors.website).toBe('Website URL must be a full URL.')
  })

  it('rejects testimonial text that is too long', () => {
    const errors = validateSpaceSubmission(
      { ...VALID, content: 'x'.repeat(2001) },
      SPACE,
    )

    expect(errors.content).toBe(
      'Testimonial text must be at most 2000 characters.',
    )
  })

  it('does not require consent', () => {
    // Consent is optional: a submitter may leave it unticked (PRD §4.3).
    const errors = validateSpaceSubmission(
      { ...VALID, consent: false },
      SPACE,
    )

    expect(errors.consent).toBeUndefined()
  })
})
