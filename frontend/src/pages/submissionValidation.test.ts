import { describe, expect, it } from 'vitest'
import {
  EMPTY_FORM,
  validateSubmission,
} from './submissionValidation'
import type { SubmissionFormValues } from './submissionValidation'

function validValues(overrides: Partial<SubmissionFormValues> = {}) {
  return {
    ...EMPTY_FORM,
    name: 'Ada Lovelace',
    role: 'Engineer',
    company: 'Analytical Engines',
    rating: 5,
    content: 'This testimonial is definitely long enough to pass validation.',
    consent: true,
    ...overrides,
  }
}

describe('validateSubmission', () => {
  it('accepts a complete, valid submission', () => {
    expect(validateSubmission(validValues())).toEqual({})
  })

  it('requires a name', () => {
    expect(validateSubmission(validValues({ name: '' })).name).toBe(
      'Name is required.',
    )
  })

  it('rejects a name shorter than 2 characters', () => {
    expect(validateSubmission(validValues({ name: 'A' })).name).toBe(
      'Name must be between 2 and 100 characters.',
    )
  })

  it('rejects a name longer than 100 characters', () => {
    expect(
      validateSubmission(validValues({ name: 'a'.repeat(101) })).name,
    ).toBe('Name must be between 2 and 100 characters.')
  })

  it('measures the trimmed name, not the raw one', () => {
    expect(validateSubmission(validValues({ name: '   ' })).name).toBe(
      'Name is required.',
    )
    expect(validateSubmission(validValues({ name: '  Ada  ' })).name).toBeUndefined()
  })

  it('allows an omitted role and company', () => {
    const errors = validateSubmission(validValues({ role: '', company: '' }))
    expect(errors.role).toBeUndefined()
    expect(errors.company).toBeUndefined()
  })

  it('rejects a role longer than 120 characters', () => {
    expect(validateSubmission(validValues({ role: 'r'.repeat(121) })).role).toBe(
      'Role must be at most 120 characters.',
    )
  })

  it('rejects a company longer than 120 characters', () => {
    expect(
      validateSubmission(validValues({ company: 'c'.repeat(121) })).company,
    ).toBe('Company must be at most 120 characters.')
  })

  it('requires a rating', () => {
    expect(validateSubmission(validValues({ rating: null })).rating).toBe(
      'Rating is required.',
    )
  })

  it('rejects a rating outside 1-5', () => {
    expect(validateSubmission(validValues({ rating: 0 })).rating).toBe(
      'Rating must be between 1 and 5.',
    )
    expect(validateSubmission(validValues({ rating: 6 })).rating).toBe(
      'Rating must be between 1 and 5.',
    )
  })

  it('requires testimonial text', () => {
    expect(validateSubmission(validValues({ content: '' })).content).toBe(
      'Testimonial text is required.',
    )
  })

  it('rejects text shorter than 20 characters', () => {
    expect(validateSubmission(validValues({ content: 'Too short' })).content).toBe(
      'Testimonial text must be between 20 and 2000 characters.',
    )
  })

  it('rejects text longer than 2000 characters', () => {
    expect(
      validateSubmission(validValues({ content: 'x'.repeat(2001) })).content,
    ).toBe('Testimonial text must be between 20 and 2000 characters.')
  })

  it('requires consent', () => {
    expect(validateSubmission(validValues({ consent: false })).consent).toBe(
      'You must consent to the publication of your testimonial.',
    )
  })

  it('reports every invalid field at once', () => {
    const errors = validateSubmission(EMPTY_FORM)
    expect(Object.keys(errors).sort()).toEqual([
      'consent',
      'content',
      'name',
      'rating',
    ])
  })
})
