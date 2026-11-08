import { useState } from 'react'
import { ApiError, createTestimonial } from '../api/client'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { StarRating } from '../components/StarRating'
import {
  CONTENT_MAX,
  EMPTY_FORM,
  validateSubmission,
} from './submissionValidation'
import type {
  SubmissionFieldErrors,
  SubmissionFormValues,
} from './submissionValidation'

/**
 * The consent wording shown next to the checkbox.
 *
 * The API stores a `consentTextVersion` alongside every submission so the
 * wording that was agreed to can be evidenced later (FR-1.9). The version
 * identifier itself lives in the backend configuration.
 */
const CONSENT_STATEMENT =
  'I give permission for LoveProof to publish my testimonial, including my name, role, company and the text above, on its public testimonial wall.'

const INPUT_CLASSES =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 placeholder:text-slate-400 focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500 disabled:bg-slate-50'

/**
 * Describes how many fields need attention.
 *
 * The summary is announced politely so a screen-reader user hears that the
 * submission failed without the focus being moved out of the form.
 */
function describeErrorCount(count: number): string {
  return count === 1
    ? 'There is 1 problem to fix.'
    : `There are ${count} problems to fix.`
}

/**
 * The public submission form.
 *
 * The flow is idle → submitting → success, with the server's field errors
 * merged into the client-side ones so a single message per field is shown.
 */
export default function SubmitTestimonialPage() {
  const [values, setValues] = useState<SubmissionFormValues>(EMPTY_FORM)
  const [errors, setErrors] = useState<SubmissionFieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [submissionError, setSubmissionError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)
  const [summaryMessage, setSummaryMessage] = useState<string | null>(null)

  const update = <K extends keyof SubmissionFormValues>(
    key: K,
    value: SubmissionFormValues[K],
  ) => {
    setValues((current) => ({ ...current, [key]: value }))
    // Clear the message as soon as the visitor starts correcting the field.
    setErrors((current) => {
      if (!current[key]) {
        return current
      }
      const next = { ...current }
      delete next[key]
      return next
    })
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSubmissionError(null)
    setSummaryMessage(null)

    const validationErrors = validateSubmission(values)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      setSummaryMessage(
        `Your testimonial was not submitted. ${describeErrorCount(
          Object.keys(validationErrors).length,
        )}`,
      )
      return
    }

    setSubmitting(true)
    try {
      await createTestimonial({
        name: values.name.trim(),
        role: values.role.trim() || null,
        company: values.company.trim() || null,
        rating: values.rating as number,
        content: values.content.trim(),
        consent: values.consent,
      })
      setSubmitted(true)
      setErrors({})
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors as SubmissionFieldErrors)
        if (Object.keys(error.fieldErrors).length > 0) {
          setSummaryMessage(
            `Your testimonial was not submitted. ${describeErrorCount(
              Object.keys(error.fieldErrors).length,
            )}`,
          )
          setSubmissionError(
            'Some details need fixing. The messages below are next to each field.',
          )
        } else {
          setSubmissionError(error.message)
        }
      } else {
        setSubmissionError(
          'We could not reach the server. Please check your connection and try again.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (submitted) {
    return (
      <div className="flex flex-col gap-6">
        <Card className="p-6 sm:p-8">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Thank you for your testimonial
          </h1>
          <p className="mt-3 text-slate-600">
            Your testimonial has been received and is now awaiting review by a
            moderator. It is not published yet — you will see it on the public
            wall once it has been approved.
          </p>
          <div className="mt-6">
            <Button
              onClick={() => {
                setValues(EMPTY_FORM)
                setSubmitted(false)
                setSubmissionError(null)
              }}
            >
              Submit another testimonial
            </Button>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-slate-900">
          Share your experience
        </h1>
        <p className="mt-2 text-slate-600">
          Tell us what you think. Every testimonial is reviewed before it is
          published.
        </p>
      </div>

      <Card className="p-6 sm:p-8">
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-6">
          <div aria-live="polite">
            {summaryMessage && (
              <p className="text-sm font-semibold text-rose-700">
                {summaryMessage}
              </p>
            )}
          </div>

          <div aria-live="assertive">
            {submissionError && (
              <ErrorBanner
                title="Your testimonial was not submitted"
                message={submissionError}
                onDismiss={() => setSubmissionError(null)}
              />
            )}
          </div>

          <Field label="Name" required error={errors.name}>
            {(fieldProps) => (
              <input
                {...fieldProps}
                type="text"
                name="name"
                autoComplete="name"
                maxLength={100}
                value={values.name}
                onChange={(event) => update('name', event.target.value)}
                className={INPUT_CLASSES}
              />
            )}
          </Field>

          <Field
            label="Role"
            hint="Optional — your job title."
            error={errors.role}
          >
            {(fieldProps) => (
              <input
                {...fieldProps}
                type="text"
                name="role"
                autoComplete="organization-title"
                maxLength={120}
                value={values.role}
                onChange={(event) => update('role', event.target.value)}
                className={INPUT_CLASSES}
              />
            )}
          </Field>

          <Field
            label="Company"
            hint="Optional — the company you work for."
            error={errors.company}
          >
            {(fieldProps) => (
              <input
                {...fieldProps}
                type="text"
                name="company"
                autoComplete="organization"
                maxLength={120}
                value={values.company}
                onChange={(event) => update('company', event.target.value)}
                className={INPUT_CLASSES}
              />
            )}
          </Field>

          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-slate-900">
              Rating
              <span className="ml-1 text-rose-600" aria-hidden="true">
                *
              </span>
              <span className="sr-only"> (required)</span>
            </span>
            <StarRating
              value={values.rating}
              onChange={(rating) => update('rating', rating)}
              error={errors.rating}
            />
          </div>

          <Field
            label="Your testimonial"
            required
            error={errors.content}
            hint={`Between 20 and ${CONTENT_MAX} characters. ${values.content.trim().length} so far.`}
          >
            {(fieldProps) => (
              <textarea
                {...fieldProps}
                name="content"
                rows={6}
                maxLength={CONTENT_MAX}
                value={values.content}
                onChange={(event) => update('content', event.target.value)}
                className={`${INPUT_CLASSES} resize-y`}
              />
            )}
          </Field>

          <div className="flex flex-col gap-1.5">
            <label className="flex items-start gap-3 text-sm text-slate-700">
              <input
                type="checkbox"
                name="consent"
                checked={values.consent}
                onChange={(event) => update('consent', event.target.checked)}
                aria-describedby={errors.consent ? 'consent-error' : undefined}
                aria-invalid={errors.consent ? true : undefined}
                className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              />
              <span>
                {CONSENT_STATEMENT}
                <span className="ml-1 text-rose-600" aria-hidden="true">
                  *
                </span>
                <span className="sr-only"> (required)</span>
              </span>
            </label>
            {errors.consent && (
              <p id="consent-error" className="text-sm font-medium text-rose-600">
                {errors.consent}
              </p>
            )}
          </div>

          <div className="flex items-center gap-3">
            <Button type="submit" loading={submitting}>
              {submitting ? 'Submitting…' : 'Submit testimonial'}
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
