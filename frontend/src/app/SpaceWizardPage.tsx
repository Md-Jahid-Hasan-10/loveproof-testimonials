import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, createSpace, listThemes, updateSpace } from '../api'
import type { Theme } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { useCustomer } from './useCustomer'

/**
 * The Space creation wizard.
 *
 * The PRD describes it as four steps -- title and subtitle, configure
 * fields, choose a theme, ratings on or off -- followed by a save. This
 * implements it as one page with one step visible at a time, because a
 * wizard that hides the whole form behind "next" makes it harder to see
 * what the Space will look like before saving it.
 *
 * The theme list comes from the server, which is the only place that knows
 * which themes are premium. A premium theme on a Free account is refused by
 * the server; the interface marks it so the customer is not surprised.
 */
const STEPS = ['Details', 'Fields', 'Theme', 'Ratings'] as const

type Step = (typeof STEPS)[number]

/**
 * The optional fields a Space can collect.
 *
 * The fixed fields -- name and email -- are always on and always required,
 * so they are not in this list: the PRD says they cannot be disabled.
 */
const OPTIONAL_FIELDS = [
  {
    key: 'fieldCompany',
    requiredKey: 'fieldCompanyRequired',
    label: 'Company name',
    hint: 'Where the person works.',
  },
  {
    key: 'fieldSocial',
    requiredKey: 'fieldSocialRequired',
    label: 'Social media links',
    hint: 'One link to their profile.',
  },
  {
    key: 'fieldWebsite',
    requiredKey: 'fieldWebsiteRequired',
    label: 'Website URL',
    hint: 'Their own website or portfolio.',
  },
  {
    key: 'fieldJobTitle',
    requiredKey: 'fieldJobTitleRequired',
    label: 'Job title',
    hint: 'How they describe their role.',
  },
] as const

export default function SpaceWizardPage() {
  const { customer } = useCustomer()
  const navigate = useNavigate()

  const [step, setStep] = useState<Step>('Details')
  const [title, setTitle] = useState('')
  const [subtitle, setSubtitle] = useState('')
  const [slug, setSlug] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [theme, setTheme] = useState('basic')
  const [ratingEnabled, setRatingEnabled] = useState(false)
  const [fields, setFields] = useState<Record<string, boolean>>({
    fieldCompany: false,
    fieldSocial: false,
    fieldWebsite: false,
    fieldJobTitle: false,
  })
  const [required, setRequired] = useState<Record<string, boolean>>({
    fieldCompanyRequired: false,
    fieldSocialRequired: false,
    fieldWebsiteRequired: false,
    fieldJobTitleRequired: false,
  })
  const [themes, setThemes] = useState<Theme[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const load = async () => {
      try {
        setThemes(await listThemes())
      } catch {
        // The theme picker falls back to the basic theme if the list cannot
        // be read; the server will refuse a bad theme anyway.
        setThemes([])
      }
    }
    void load()
  }, [])

  /**
   * The slug the server would derive from the title.
   *
   * The interface shows this as a preview, not as a promise: the server
   * derives its own slug and appends a number when the obvious one is taken.
   */
  const derivedSlug = useMemo(() => {
    // The server folds accents the same way: NFD, then drop the combining
    // diacritical marks, which live in the U+0300 to U+036F block.
    const folded = Array.from(
      title.normalize('NFD').toLowerCase(),
    )
      .filter((character) => {
        const code = character.codePointAt(0) ?? 0
        return code < 0x0300 || code > 0x036f
      })
      .join('')
    const cleaned = folded
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
    return cleaned.slice(0, 80)
  }, [title])

  const effectiveSlug = slugTouched ? slug : derivedSlug

  const canAdvance = step === 'Details' ? title.trim() !== '' : true

  const handleSubmit = async () => {
    setError(null)
    setSubmitting(true)
    try {
      const created = await createSpace({
        title: title.trim(),
        subtitle: subtitle.trim() === '' ? null : subtitle.trim(),
        slug: effectiveSlug === '' ? null : effectiveSlug,
        theme,
      })

      // The form configuration and the rating setting are saved afterwards,
      // so the Space exists first and the owner is taken to a page that can
      // show the public URL straight away.
      await updateSpace(created.id, {
        ratingEnabled,
        ...fields,
        ...required,
      })

      navigate(`/app/spaces/${created.id}?created=1`, { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'PLAN_LIMIT_REACHED') {
        setError(
          `${cause.message} You can upgrade from the settings page.`,
        )
      } else {
        setError(
          cause instanceof Error
            ? cause.message
            : 'The Space could not be created.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  const stepIndex = STEPS.indexOf(step)

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Create a Space
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          A Space collects testimonials on its own public page.
        </p>
      </div>

      <ol className="flex flex-wrap gap-2" aria-label="Steps">
        {STEPS.map((name, index) => (
          <li key={name}>
            <button
              type="button"
              onClick={() => index <= stepIndex && setStep(name)}
              disabled={index > stepIndex}
              aria-current={name === step ? 'step' : undefined}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium ${
                name === step
                  ? 'bg-brand-600 text-white'
                  : index < stepIndex
                    ? 'bg-brand-50 text-brand-700'
                    : 'bg-slate-100 text-slate-500'
              }`}
            >
              {index + 1}. {name}
            </button>
          </li>
        ))}
      </ol>

      {error && <ErrorBanner message={error} />}

      <Card className="p-6">
        {step === 'Details' && (
          <div className="space-y-4">
            <Field
              label="Space title"
              hint="What this Space is called, for example “Customer reviews”."
              required
            >
              {(field) => (
                <input
                  {...field}
                  name="title"
                  type="text"
                  required
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                  className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              )}
            </Field>

            <Field
              label="Subtitle"
              hint="Optional. A short line shown under the title on the public page."
            >
              {(field) => (
                <input
                  {...field}
                  name="subtitle"
                  type="text"
                  value={subtitle}
                  onChange={(event) => setSubtitle(event.target.value)}
                  className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              )}
            </Field>

            <Field
              label="Public address"
              hint="The address your customers will use. You can change it until the first testimonial arrives."
            >
              {(field) => (
                <div className="flex items-center gap-2">
                  <span className="text-sm text-slate-500">/s/</span>
                  <input
                    {...field}
                    name="slug"
                    type="text"
                    value={effectiveSlug}
                    onChange={(event) => {
                      setSlugTouched(true)
                      setSlug(event.target.value)
                    }}
                    className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                  />
                </div>
              )}
            </Field>
          </div>
        )}

        {step === 'Fields' && (
          <div className="space-y-4">
            <div>
              <h2 className="text-base font-semibold text-slate-900">
                Optional fields
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                Name and email are always collected and always required. Add
                whatever else you want to ask for.
              </p>
            </div>

            {OPTIONAL_FIELDS.map((field) => (
              <fieldset
                key={field.key}
                className="rounded-lg border border-slate-200 p-4"
              >
                <legend className="sr-only">{field.label}</legend>

                <div className="flex items-start justify-between gap-4">
                  <div>
                    <p className="text-sm font-medium text-slate-900">
                      {field.label}
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {field.hint}
                    </p>
                  </div>

                  <label className="flex shrink-0 items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      aria-label={`Ask for ${field.label.toLowerCase()}`}
                      checked={fields[field.key]}
                      onChange={(event) => {
                        const enabled = event.target.checked
                        setFields((current) => ({
                          ...current,
                          [field.key]: enabled,
                        }))
                        if (!enabled) {
                          setRequired((current) => ({
                            ...current,
                            [field.requiredKey]: false,
                          }))
                        }
                      }}
                      className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                    />
                    Ask for it
                  </label>
                </div>

                {fields[field.key] && (
                  <label className="mt-3 flex items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      aria-label={`Make ${field.label.toLowerCase()} required`}
                      checked={required[field.requiredKey]}
                      onChange={(event) =>
                        setRequired((current) => ({
                          ...current,
                          [field.requiredKey]: event.target.checked,
                        }))
                      }
                      className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                    />
                    Make it required
                  </label>
                )}
              </fieldset>
            ))}
          </div>
        )}

        {step === 'Theme' && (
          <div className="space-y-4">
            <div>
              <h2 className="text-base font-semibold text-slate-900">
                Theme
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                How the public page looks.
              </p>
            </div>

            {themes.length === 0 ? (
              <p className="text-sm text-slate-600">Loading the themes…</p>
            ) : (
              <div
                role="radiogroup"
                aria-label="Theme"
                className="grid gap-3 sm:grid-cols-2"
              >
                {themes.map((candidate) => {
                  const locked =
                    candidate.premium && customer?.plan !== 'PREMIUM'
                  return (
                    <label
                      key={candidate.key}
                      className={`flex cursor-pointer items-start gap-3 rounded-lg border p-4 ${
                        theme === candidate.key
                          ? 'border-brand-500 bg-brand-50'
                          : 'border-slate-200'
                      } ${locked ? 'opacity-70' : ''}`}
                    >
                      <input
                        type="radio"
                        name="theme"
                        value={candidate.key}
                        aria-label={candidate.label}
                        checked={theme === candidate.key}
                        disabled={locked}
                        onChange={() => setTheme(candidate.key)}
                        className="mt-1 h-4 w-4 border-slate-300 text-brand-600 focus:ring-brand-500"
                      />
                      <span>
                        <span className="block text-sm font-medium text-slate-900">
                          {candidate.label}
                        </span>
                        {locked && (
                          <span className="mt-1 block text-xs font-medium text-amber-800">
                            Needs Premium
                          </span>
                        )}
                      </span>
                    </label>
                  )
                })}
              </div>
            )}
          </div>
        )}

        {step === 'Ratings' && (
          <div className="space-y-4">
            <div>
              <h2 className="text-base font-semibold text-slate-900">
                Ratings
              </h2>
              <p className="mt-1 text-sm text-slate-600">
                Whether customers can leave a star rating alongside their
                words.
              </p>
            </div>

            <label className="flex items-center gap-3 rounded-lg border border-slate-200 p-4">
              <input
                type="checkbox"
                checked={ratingEnabled}
                onChange={(event) => setRatingEnabled(event.target.checked)}
                className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              />
              <span className="text-sm font-medium text-slate-900">
                Ask for a rating
              </span>
            </label>
          </div>
        )}

        <div className="mt-6 flex items-center justify-between border-t border-slate-200 pt-4">
          <Button
            variant="secondary"
            onClick={() => setStep(STEPS[Math.max(0, stepIndex - 1)])}
            disabled={stepIndex === 0 || submitting}
          >
            Back
          </Button>

          {stepIndex < STEPS.length - 1 ? (
            <Button
              onClick={() => setStep(STEPS[stepIndex + 1])}
              disabled={!canAdvance}
            >
              Next
            </Button>
          ) : (
            <Button onClick={() => void handleSubmit()} loading={submitting}>
              {submitting ? 'Creating…' : 'Create Space'}
            </Button>
          )}
        </div>
      </Card>
    </div>
  )
}
