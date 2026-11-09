import { useCallback, useEffect, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, getSpace, listThemes, updateSpace } from '../api'
import type { Space, Theme } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { useCustomer } from './useCustomer'

/**
 * One Space: its public URL, its details and its form configuration.
 *
 * This is where the owner edits what the public page collects. Editing the
 * form never touches the testimonials that were already collected -- the
 * configuration lives on the Space, and the submissions carry their own
 * copy of what they were asked for.
 */
export default function SpaceDetailPage() {
  const { spaceId } = useParams<{ spaceId: string }>()
  const [searchParams] = useSearchParams()
  const { customer } = useCustomer()

  const [space, setSpace] = useState<Space | null>(null)
  const [themes, setThemes] = useState<Theme[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const [title, setTitle] = useState('')
  const [subtitle, setSubtitle] = useState('')
  const [slug, setSlug] = useState('')
  const [theme, setTheme] = useState('basic')
  const [ratingEnabled, setRatingEnabled] = useState(false)
  const [fields, setFields] = useState<Record<string, boolean>>({})
  const [required, setRequired] = useState<Record<string, boolean>>({})

  const load = useCallback(async () => {
    if (!spaceId) {
      return
    }
    setLoading(true)
    try {
      const [loaded, availableThemes] = await Promise.all([
        getSpace(spaceId),
        listThemes().catch(() => [] as Theme[]),
      ])
      setSpace(loaded)
      setThemes(availableThemes)
      setTitle(loaded.title)
      setSubtitle(loaded.subtitle ?? '')
      setSlug(loaded.slug)
      setTheme(loaded.theme)
      setRatingEnabled(loaded.ratingEnabled)
      setFields({
        fieldCompany: loaded.fieldCompany,
        fieldSocial: loaded.fieldSocial,
        fieldWebsite: loaded.fieldWebsite,
        fieldJobTitle: loaded.fieldJobTitle,
      })
      setRequired({
        fieldCompanyRequired: loaded.fieldCompanyRequired,
        fieldSocialRequired: loaded.fieldSocialRequired,
        fieldWebsiteRequired: loaded.fieldWebsiteRequired,
        fieldJobTitleRequired: loaded.fieldJobTitleRequired,
      })
      setError(null)
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'That Space could not be loaded.',
      )
    } finally {
      setLoading(false)
    }
  }, [spaceId])

  useEffect(() => {
    void load()
  }, [load])

  const handleSave = async () => {
    if (!space) {
      return
    }
    setError(null)
    setNotice(null)
    setSaving(true)
    try {
      const updated = await updateSpace(space.id, {
        title: title.trim(),
        subtitle: subtitle.trim() === '' ? null : subtitle.trim(),
        slug,
        theme,
        ratingEnabled,
        ...fields,
        ...required,
      })
      setSpace(updated)
      setNotice('Your changes were saved.')
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'CONFLICT') {
        setError(cause.message)
      } else {
        setError(
          cause instanceof Error
            ? cause.message
            : 'Your changes could not be saved.',
        )
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-600">
        Loading your Space…
      </p>
    )
  }

  if (space === null) {
    return (
      <div className="space-y-4">
        <ErrorBanner
          title="Space not found"
          message={error ?? 'That Space does not exist.'}
        />
        <Link to="/app" className="text-sm font-medium text-brand-700 underline">
          Back to your Spaces
        </Link>
      </div>
    )
  }

  const created = searchParams.get('created') === '1'

  return (
    <div className="space-y-6">
      {created && (
        <div
          role="status"
          className="rounded-card border border-emerald-200 bg-gradient-to-r from-emerald-50 to-teal-50/60 p-4"
        >
          <p className="text-sm font-semibold text-emerald-900">
            Your Space is ready.
          </p>
          <p className="mt-1 text-sm text-emerald-800">
            Share the link below and your testimonials will start arriving.
          </p>
        </div>
      )}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="eyebrow">Space</p>
          <h1 className="page-title mt-1.5 truncate">{space.title}</h1>
          <p className="page-subtitle">
            Public page at{' '}
            <a
              href={space.publicUrl}
              className="font-mono text-brand-700 underline underline-offset-2 hover:text-brand-800"
              target="_blank"
              rel="noreferrer"
            >
              {space.publicUrl}
            </a>
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Link to={`/app/spaces/${space.id}/embed`}>
            <Button variant="secondary">Wall of Love</Button>
          </Link>
          <Link to={`/app/spaces/${space.id}/inbox`}>
            <Button variant="secondary">Testimonials</Button>
          </Link>
        </div>
      </div>

      {error && <ErrorBanner message={error} />}
      {notice && (
        <p
          role="status"
          className="rounded-card border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900"
        >
          {notice}
        </p>
      )}

      {!space.editable && (
        <div className="rounded-card border border-amber-200 bg-gradient-to-r from-amber-50 to-orange-50/60 p-4">
          <p className="text-sm font-semibold text-amber-900">
            This Space is read-only.
          </p>
          <p className="mt-1 text-sm text-amber-800">
            Your plan does not include this many Spaces. Your three oldest
            Spaces stay editable; this one is kept but cannot be changed.
            Nothing has been deleted.
          </p>
        </div>
      )}

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">Details</h2>

        <div className="mt-4 space-y-4">
          <Field label="Space title" required>
            {(field) => (
              <input
                {...field}
                name="title"
                type="text"
                disabled={!space.editable}
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                className="control"
              />
            )}
          </Field>

          <Field label="Subtitle">
            {(field) => (
              <input
                {...field}
                name="subtitle"
                type="text"
                disabled={!space.editable}
                value={subtitle}
                onChange={(event) => setSubtitle(event.target.value)}
                className="control"
              />
            )}
          </Field>

          <Field
            label="Public address"
            hint={
              space.slugLocked
                ? 'This address cannot change any more — the Space has already received a testimonial.'
                : 'You can change this until the first testimonial arrives.'
            }
          >
            {(field) => (
              <div className="flex items-center gap-2">
                <span className="text-sm text-slate-500">/s/</span>
                <input
                  {...field}
                  name="slug"
                  type="text"
                  disabled={!space.editable || space.slugLocked}
                  value={slug}
                  onChange={(event) => setSlug(event.target.value)}
                  className="control"
                />
              </div>
            )}
          </Field>
        </div>
      </Card>

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">What the form asks for</h2>
        <p className="section-subtitle">
          Name and email are always collected and always required. Changing
          this never changes the testimonials you have already collected.
        </p>

        <ul className="mt-4 space-y-3">
          {[
            {
              key: 'fieldCompany',
              requiredKey: 'fieldCompanyRequired',
              label: 'Company name',
            },
            {
              key: 'fieldSocial',
              requiredKey: 'fieldSocialRequired',
              label: 'Social media links',
            },
            {
              key: 'fieldWebsite',
              requiredKey: 'fieldWebsiteRequired',
              label: 'Website URL',
            },
            {
              key: 'fieldJobTitle',
              requiredKey: 'fieldJobTitleRequired',
              label: 'Job title',
            },
          ].map((field) => (
            <li
              key={field.key}
              className="flex flex-wrap items-center justify-between gap-3 rounded-card border border-slate-200/80 bg-slate-50/60 p-3"
            >
              <span className="text-sm font-medium text-slate-900">
                {field.label}
              </span>

              <span className="flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    type="checkbox"
                    aria-label={`Ask for ${field.label.toLowerCase()}`}
                    disabled={!space.editable}
                    checked={fields[field.key] ?? false}
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

                {fields[field.key] && (
                  <label className="flex items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      aria-label={`Make ${field.label.toLowerCase()} required`}
                      disabled={!space.editable}
                      checked={required[field.requiredKey] ?? false}
                      onChange={(event) =>
                        setRequired((current) => ({
                          ...current,
                          [field.requiredKey]: event.target.checked,
                        }))
                      }
                      className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                    />
                    Required
                  </label>
                )}
              </span>
            </li>
          ))}
        </ul>

        <label className="mt-4 flex items-center gap-3 rounded-card border border-slate-200/80 bg-slate-50/60 p-3">
          <input
            type="checkbox"
            disabled={!space.editable}
            checked={ratingEnabled}
            onChange={(event) => setRatingEnabled(event.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
          />
          <span className="text-sm font-medium text-slate-900">
            Ask for a rating
          </span>
        </label>
      </Card>

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">Theme</h2>

        <div
          role="radiogroup"
          aria-label="Theme"
          className="mt-4 grid gap-3 sm:grid-cols-2"
        >
          {themes.map((candidate) => {
            const locked =
              candidate.premium && customer?.plan !== 'PREMIUM'
            return (
              <label
                key={candidate.key}
                className={`flex items-start gap-3 rounded-card border p-3 transition ${
                  theme === candidate.key
                    ? 'border-brand-500 bg-brand-50 shadow-card ring-1 ring-brand-200'
                    : 'border-slate-200 bg-white hover:border-slate-300 hover:shadow-card'
                } ${locked ? 'opacity-70' : ''}`}
              >
                <input
                  type="radio"
                  name="theme"
                  value={candidate.key}
                  aria-label={candidate.label}
                  disabled={!space.editable || locked}
                  checked={theme === candidate.key}
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
      </Card>

      {space.editable && (
        <div className="flex justify-end">
          <Button onClick={() => void handleSave()} loading={saving}>
            {saving ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      )}
    </div>
  )
}
