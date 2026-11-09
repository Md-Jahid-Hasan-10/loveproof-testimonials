import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  ApiError,
  getPublicSpace,
  submitSpaceTestimonial,
} from '../api'
import type { PublicSpace } from '../api'
import { Button } from '../components/Button'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { SpaceTheme } from '../components/SpaceTheme'
import { StarRating } from '../components/StarRating'
import {
  EMPTY_SPACE_SUBMISSION,
  validateSpaceSubmission,
} from './spaceSubmissionValidation'
import type {
  SpaceSubmissionFieldErrors,
  SpaceSubmissionValues,
} from './spaceSubmissionValidation'

import celebrateGif from '../assets/thankyou/celebrate.gif'
import confettiGif from '../assets/thankyou/confetti.gif'
import starGif from '../assets/thankyou/star.gif'
import thumbGif from '../assets/thankyou/thumb.gif'

/**
 * The public page of one Space.
 *
 * <p>A visitor who has never signed in fills this in. The page shows only
 * what the Space asks for, so a Space that does not want a company name does
 * not show a company name field (PRD §4.5).
 *
 * <p>After a successful submission the page says thank you and shows one of
 * the bundled animations, picked at random.
 *
 * <p>The submitter's email is never shown back to them and never leaves the
 * browser except as part of the submission.
 */
const THANK_YOU_GIFS = [celebrateGif, confettiGif, starGif, thumbGif]

/**
 * Picks one of the bundled animations.
 *
 * <p>The choice is made once, when the submission succeeds, so the picture
 * does not change while the visitor is looking at it.
 *
 * @returns the URL of the chosen animation
 */
function pickThankYouGif(): string {
  return THANK_YOU_GIFS[Math.floor(Math.random() * THANK_YOU_GIFS.length)]
}

export default function SpaceSubmitPage() {
  const { slug } = useParams<{ slug: string }>()

  const [space, setSpace] = useState<PublicSpace | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false)

  const [values, setValues] = useState<SpaceSubmissionValues>(
    EMPTY_SPACE_SUBMISSION,
  )
  const [errors, setErrors] = useState<SpaceSubmissionFieldErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [thankYouGif, setThankYouGif] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!slug) {
      return
    }
    setLoading(true)
    try {
      const loaded = await getPublicSpace(slug)
      setSpace(loaded)
      setLoadError(null)
      setNotFound(false)
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 404) {
        setNotFound(true)
      } else {
        setLoadError(
          cause instanceof Error
            ? cause.message
            : 'This page could not be loaded.',
        )
      }
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    void load()
  }, [load])

  const setValue = <K extends keyof SpaceSubmissionValues>(
    key: K,
    value: SpaceSubmissionValues[K],
  ) => {
    setValues((current) => ({ ...current, [key]: value }))
    // Clearing the error as soon as the field changes keeps the form from
    // arguing with the visitor while they are fixing it.
    setErrors((current) => {
      if (!(key in current)) {
        return current
      }
      const next = { ...current }
      delete next[key]
      return next
    })
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!space || !slug) {
      return
    }

    const found = validateSpaceSubmission(values, space)
    setErrors(found)
    if (Object.keys(found).length > 0) {
      return
    }

    setSubmitError(null)
    setSubmitting(true)
    try {
      await submitSpaceTestimonial(slug, {
        name: values.name.trim(),
        email: values.email.trim(),
        content: values.content.trim(),
        rating: space.ratingEnabled ? values.rating : null,
        company: space.fieldCompany ? values.company.trim() || null : null,
        socialLink: space.fieldSocial ? values.socialLink.trim() || null : null,
        website: space.fieldWebsite ? values.website.trim() || null : null,
        jobTitle: space.fieldJobTitle ? values.jobTitle.trim() || null : null,
        consent: values.consent,
        // The honeypot is always empty for a real submission. A bot fills it
        // and is quietly discarded.
        website2: '',
      })
      setThankYouGif(pickThankYouGif())
      setSubmitted(true)
    } catch (cause) {
      if (cause instanceof ApiError) {
        if (cause.fieldErrors && Object.keys(cause.fieldErrors).length > 0) {
          // The server's per-field messages belong next to their inputs.
          setErrors(cause.fieldErrors)
        }
        if (cause.code === 'SPACE_NOT_ACCEPTING') {
          setSubmitError(
            'This Space is not accepting responses at the moment.',
          )
        } else if (cause.isRateLimited) {
          setSubmitError(
            'Too many submissions came from this location. Please try again later.',
          )
        } else {
          setSubmitError(cause.message)
        }
      } else {
        setSubmitError(
          cause instanceof Error
            ? cause.message
            : 'Your testimonial could not be sent.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  const pageTitle = useMemo(
    () => space?.title ?? 'Share your experience',
    [space],
  )

  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-600">
        Loading…
      </p>
    )
  }

  if (notFound) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          This page does not exist
        </h1>
        <p className="text-sm text-slate-600">
          The link may be wrong, or the Space it pointed at has been removed.
        </p>
      </div>
    )
  }

  if (space === null) {
    return (
      <div className="mx-auto max-w-2xl">
        <ErrorBanner
          title="This page could not be loaded"
          message={loadError ?? 'Please try again later.'}
          onRetry={() => void load()}
        />
      </div>
    )
  }

  if (submitted) {
    return (
      <SpaceTheme theme={space.theme}>
        <div className="flex flex-col items-center gap-6 text-center">
          <img
            src={thankYouGif ?? celebrateGif}
            alt=""
            width={240}
            height={180}
            className="rounded-card"
          />
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">
            Thank you!
          </h1>
          <p className="text-base leading-relaxed text-slate-600">
            Your testimonial has been sent to {space.title}. It is now live.
          </p>
          <p className="text-sm text-slate-500">
            You can close this page.
          </p>
        </div>
      </SpaceTheme>
    )
  }

  if (!space.accepting) {
    return (
      <SpaceTheme theme={space.theme}>
        <div className="flex flex-col items-center gap-4 text-center">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            {pageTitle}
          </h1>
          <p className="text-base text-slate-600">
            This Space is not accepting responses at the moment.
          </p>
          {space.subtitle && (
            <p className="text-sm text-slate-500">{space.subtitle}</p>
          )}
        </div>
      </SpaceTheme>
    )
  }

  return (
    <SpaceTheme theme={space.theme}>
      <div className="space-y-8">
        <header className="space-y-2 text-center">
          <h1 className="text-3xl font-bold tracking-tight text-slate-900">
            {pageTitle}
          </h1>
          {space.subtitle && (
            <p className="text-base leading-relaxed text-slate-600">
              {space.subtitle}
            </p>
          )}
        </header>

        {submitError && <ErrorBanner message={submitError} />}

        <div className="surface-raised p-6 sm:p-8">
          <form onSubmit={handleSubmit} noValidate className="space-y-5">
          <Field label="Your name" required error={errors.name}>
            {(field) => (
              <input
                {...field}
                name="name"
                type="text"
                autoComplete="name"
                maxLength={100}
                value={values.name}
                onChange={(event) => setValue('name', event.target.value)}
                className="control"
              />
            )}
          </Field>

          <Field
            label="Your email"
            required
            error={errors.email}
            hint="Never shown publicly. It is only used to recognise a repeat submission."
          >
            {(field) => (
              <input
                {...field}
                name="email"
                type="email"
                autoComplete="email"
                maxLength={254}
                value={values.email}
                onChange={(event) => setValue('email', event.target.value)}
                className="control"
              />
            )}
          </Field>

          {space.fieldCompany && (
            <Field
              label="Company name"
              required={space.fieldCompanyRequired}
              error={errors.company}
            >
              {(field) => (
                <input
                  {...field}
                  name="company"
                  type="text"
                  autoComplete="organization"
                  maxLength={120}
                  value={values.company}
                  onChange={(event) =>
                    setValue('company', event.target.value)
                  }
                  className="control"
                />
              )}
            </Field>
          )}

          {space.fieldJobTitle && (
            <Field
              label="Job title"
              required={space.fieldJobTitleRequired}
              error={errors.jobTitle}
            >
              {(field) => (
                <input
                  {...field}
                  name="jobTitle"
                  type="text"
                  autoComplete="organization-title"
                  maxLength={120}
                  value={values.jobTitle}
                  onChange={(event) =>
                    setValue('jobTitle', event.target.value)
                  }
                  className="control"
                />
              )}
            </Field>
          )}

          {space.fieldSocial && (
            <Field
              label="Social media link"
              required={space.fieldSocialRequired}
              error={errors.socialLink}
              hint="A link to one of your profiles."
            >
              {(field) => (
                <input
                  {...field}
                  name="socialLink"
                  type="url"
                  maxLength={500}
                  placeholder="https://example.com/you"
                  value={values.socialLink}
                  onChange={(event) =>
                    setValue('socialLink', event.target.value)
                  }
                  className="control"
                />
              )}
            </Field>
          )}

          {space.fieldWebsite && (
            <Field
              label="Website URL"
              required={space.fieldWebsiteRequired}
              error={errors.website}
              hint="Your own website or portfolio."
            >
              {(field) => (
                <input
                  {...field}
                  name="website"
                  type="url"
                  maxLength={500}
                  placeholder="https://example.com"
                  value={values.website}
                  onChange={(event) =>
                    setValue('website', event.target.value)
                  }
                  className="control"
                />
              )}
            </Field>
          )}

          <Field
            label="Your testimonial"
            required
            error={errors.content}
            hint={`Up to ${2000} characters.`}
          >
            {(field) => (
              <textarea
                {...field}
                name="content"
                rows={6}
                maxLength={2000}
                value={values.content}
                onChange={(event) => setValue('content', event.target.value)}
                className="control"
              />
            )}
          </Field>

          {space.ratingEnabled && (
            <div>
              <span className="text-sm font-semibold tracking-tight text-slate-800">
                Your rating
                <span className="ml-1 text-rose-600" aria-hidden="true">
                  *
                </span>
                <span className="sr-only"> (required)</span>
              </span>
              <div className="mt-1.5">
                <StarRating
                  value={values.rating}
                  onChange={(rating) => setValue('rating', rating)}
                  error={errors.rating}
                />
              </div>
            </div>
          )}

          <label className="flex items-start gap-3 rounded-card border border-slate-200/80 bg-slate-50/60 p-3">
            <input
              type="checkbox"
              name="consent"
              checked={values.consent}
              onChange={(event) => setValue('consent', event.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
            <span className="text-sm text-slate-700">
              You may publish my testimonial, including my name and the
              details above. This is optional.
            </span>
          </label>

          {/*
            The honeypot. It is hidden from people and visible to bots, so a
            bot that fills it is quietly discarded by the server.
          */}
          <div aria-hidden="true" className="absolute left-[-9999px] h-0 w-0 overflow-hidden">
            <label htmlFor="website2">Leave this field empty</label>
            <input
              id="website2"
              name="website2"
              type="text"
              tabIndex={-1}
              autoComplete="off"
              defaultValue=""
            />
          </div>

          <div className="flex justify-end">
            <Button type="submit" loading={submitting}>
              {submitting ? 'Sending…' : 'Send my testimonial'}
            </Button>
          </div>
          </form>
        </div>
      </div>
    </SpaceTheme>
  )
}
