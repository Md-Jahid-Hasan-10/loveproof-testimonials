import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  ApiError,
  getEmbedConfig,
  getPublicEmbed,
  rotateEmbedToken,
  updateEmbedConfig,
} from '../api'
import type {
  EmbedConfig,
  EmbedLayout,
  EmbedThemeMode,
  PublicEmbed,
} from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { EmbedPreview } from '../components/EmbedPreview'
import { ErrorBanner } from '../components/ErrorBanner'
import { useCustomer } from './useCustomer'

/**
 * The embed configuration for one Space.
 *
 * <p>The configuration lives on the server, so the owner can change the
 * layout without going back to their website to paste a new snippet. The
 * snippet below is the same one for every change; only the data behind it
 * moves (PRD §4.8).
 *
 * <p>The premium options are shown to everybody and marked as locked when the
 * account cannot use them. The server refuses them too, so the lock is a
 * courtesy rather than the only guard.
 */
export default function SpaceEmbedPage() {
  const { spaceId } = useParams<{ spaceId: string }>()
  const { customer } = useCustomer()

  const [config, setConfig] = useState<EmbedConfig | null>(null)
  const [preview, setPreview] = useState<PublicEmbed | null>(null)

  const [layout, setLayout] = useState<EmbedLayout>('masonry')
  const [themeMode, setThemeMode] = useState<EmbedThemeMode>('light')
  const [backgroundColor, setBackgroundColor] = useState('')
  const [animation, setAnimation] = useState(false)

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const premium = customer?.plan === 'PREMIUM'

  const load = useCallback(async () => {
    if (!spaceId) {
      return
    }
    setLoading(true)
    try {
      const loaded = await getEmbedConfig(spaceId)
      setConfig(loaded)
      setLayout(loaded.layout)
      setThemeMode(loaded.themeMode)
      setBackgroundColor(loaded.backgroundColor ?? '')
      setAnimation(loaded.animation)
      setError(null)
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'The embed could not be loaded.',
      )
    } finally {
      setLoading(false)
    }
  }, [spaceId])

  useEffect(() => {
    void load()
  }, [load])

  /**
   * The preview data.
   *
   * <p>The preview follows the settings on screen rather than the saved
   * ones, so it updates on every change (PRD §4.8). The testimonials come
   * from the public endpoint, because that is where the embed gets them.
   */
  useEffect(() => {
    if (!config) {
      return
    }
    getPublicEmbed(config.embedToken)
      .then((data) => setPreview(data))
      .catch(() => {
        // The preview is a convenience. If the public data cannot be read,
        // the page still works: the owner can still copy the snippet.
        setPreview(null)
      })
  }, [config])

  /**
   * The snippet the owner pastes into their website.
   */
  const snippet = useMemo(() => {
    if (!config) {
      return ''
    }
    return [
      `<script src="${config.scriptUrl}" async></script>`,
      `<div data-loveproof-embed="${config.embedToken}"></div>`,
    ].join('\n')
  }, [config])

  const handleSave = async () => {
    if (!spaceId) {
      return
    }
    setSaving(true)
    setError(null)
    setNotice(null)
    try {
      const saved = await updateEmbedConfig(spaceId, {
        layout,
        themeMode,
        backgroundColor: backgroundColor === '' ? null : backgroundColor,
        animation,
      })
      setConfig(saved)
      setNotice('Your embed was saved.')
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.code === 'CONFLICT'
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : 'Your embed could not be saved.',
      )
    } finally {
      setSaving(false)
    }
  }

  const handleRotate = async () => {
    if (!spaceId) {
      return
    }
    setSaving(true)
    setError(null)
    setNotice(null)
    try {
      const rotated = await rotateEmbedToken(spaceId)
      setConfig(rotated)
      setNotice(
        'A new token was generated. Update the snippet on your website — the old one no longer works.',
      )
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : 'The token could not be changed.',
      )
    } finally {
      setSaving(false)
    }
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(snippet)
      setCopied(true)
    } catch {
      // A browser that refuses the clipboard is not a failure of the page:
      // the snippet is on screen and can be selected by hand.
      setCopied(false)
    }
  }

  if (loading) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-600">
        Loading your embed…
      </p>
    )
  }

  if (!config) {
    return (
      <div className="space-y-4">
        <ErrorBanner
          title="Embed not found"
          message={error ?? 'That Space does not exist.'}
        />
        <Link to="/app" className="text-sm font-medium text-brand-700 underline">
          Back to your Spaces
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="eyebrow">Embed</p>
          <h1 className="page-title mt-1.5 truncate">Wall of Love</h1>
          <p className="page-subtitle">
            Put your testimonials on your own website. Only the ones on your
            Wall of Love, and only the ones that are visible, appear there.
          </p>
        </div>

        <Link to={`/app/spaces/${config.spaceId}/inbox`}>
          <Button variant="secondary">Manage testimonials</Button>
        </Link>
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

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">Layout</h2>

        <div
          role="radiogroup"
          aria-label="Layout"
          className="mt-4 grid gap-3 sm:grid-cols-2"
        >
          {(
            [
              { key: 'masonry', label: 'Masonry', premium: false },
              { key: 'carousel', label: 'Carousel', premium: true },
            ] as const
          ).map((option) => {
            const locked = option.premium && !premium
            return (
              <label
                key={option.key}
                className={`flex items-start gap-3 rounded-card border p-3 transition ${
                  layout === option.key
                    ? 'border-brand-500 bg-brand-50 shadow-card ring-1 ring-brand-200'
                    : 'border-slate-200 bg-white hover:border-slate-300 hover:shadow-card'
                } ${locked ? 'opacity-70' : ''}`}
              >
                <input
                  type="radio"
                  name="layout"
                  value={option.key}
                  aria-label={option.label}
                  disabled={locked}
                  checked={layout === option.key}
                  onChange={() => setLayout(option.key)}
                  className="mt-1 h-4 w-4 border-slate-300 text-brand-600 focus:ring-brand-500"
                />
                <span>
                  <span className="block text-sm font-medium text-slate-900">
                    {option.label}
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

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">Appearance</h2>

        <div
          role="radiogroup"
          aria-label="Theme mode"
          className="mt-4 grid gap-3 sm:grid-cols-2"
        >
          {(
            [
              { key: 'light', label: 'Light' },
              { key: 'dark', label: 'Dark' },
            ] as const
          ).map((option) => (
            <label
              key={option.key}
              className={`flex items-center gap-3 rounded-card border p-3 transition ${
                themeMode === option.key
                  ? 'border-brand-500 bg-brand-50 shadow-card ring-1 ring-brand-200'
                  : 'border-slate-200 bg-white hover:border-slate-300 hover:shadow-card'
              }`}
            >
              <input
                type="radio"
                name="theme-mode"
                value={option.key}
                aria-label={option.label}
                checked={themeMode === option.key}
                onChange={() => setThemeMode(option.key)}
                className="h-4 w-4 border-slate-300 text-brand-600 focus:ring-brand-500"
              />
              <span className="text-sm font-medium text-slate-900">
                {option.label}
              </span>
            </label>
          ))}
        </div>

        <div className="mt-4 space-y-4">
          <div>
            <label
              htmlFor="embed-background"
              className="block text-sm font-medium text-slate-900"
            >
              Background colour
            </label>
            <input
              id="embed-background"
              name="background"
              type="color"
              disabled={!premium}
              value={backgroundColor === '' ? '#ffffff' : backgroundColor}
              onChange={(event) => setBackgroundColor(event.target.value)}
              className="mt-1 h-10 w-20 rounded-lg border border-slate-300 disabled:bg-slate-50"
            />
            {!premium && (
              <p className="mt-1 text-xs font-medium text-amber-800">
                A background colour needs a Premium plan.
              </p>
            )}
          </div>

          <label className="flex items-center gap-3">
            <input
              type="checkbox"
              aria-label="Animation"
              disabled={!premium}
              checked={animation}
              onChange={(event) => setAnimation(event.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
            />
            <span className="text-sm font-medium text-slate-900">
              Animate the testimonials in
            </span>
            {!premium && (
              <span className="text-xs font-medium text-amber-800">
                Needs Premium
              </span>
            )}
          </label>
        </div>
      </Card>

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">Preview</h2>
        <p className="section-subtitle">
          This is how the embed will look on your website.
        </p>

        <div className="mt-5">
          <EmbedPreview
            title={preview?.title ?? config.spaceId}
            subtitle={preview?.subtitle ?? null}
            layout={layout}
            themeMode={themeMode}
            backgroundColor={backgroundColor === '' ? null : backgroundColor}
            animation={animation}
            testimonials={preview?.testimonials ?? []}
          />
        </div>
      </Card>

      <Card className="p-6" elevation="raised">
        <h2 className="section-title">Paste this into your website</h2>
        <p className="section-subtitle">
          Put it where you want the testimonials to appear. It works on any
          site, and it keeps working after you change the settings above.
        </p>

        <pre className="mt-4 overflow-x-auto rounded-control bg-slate-900 p-4 text-xs leading-relaxed text-slate-100 shadow-inner">
          <code>{snippet}</code>
        </pre>

        <div className="mt-4 flex flex-wrap gap-2">
          <Button onClick={() => void handleCopy()}>
            {copied ? 'Copied' : 'Copy the snippet'}
          </Button>
          <Button
            variant="secondary"
            loading={saving}
            onClick={() => void handleRotate()}
          >
            Generate a new token
          </Button>
        </div>

        <p className="mt-3 text-xs text-slate-500">
          A new token stops the old snippet from working, so update your
          website straight afterwards.
        </p>
      </Card>

      <div className="flex justify-end">
        <Button onClick={() => void handleSave()} loading={saving}>
          {saving ? 'Saving…' : 'Save the embed'}
        </Button>
      </div>
    </div>
  )
}
