import { useEffect, useState } from 'react'
import { changePassword, describeFailure, updateProfile } from '../api'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ErrorBanner } from '../components/ErrorBanner'
import { Field } from '../components/Field'
import { useCustomer } from './useCustomer'

/**
 * The account settings page.
 *
 * <p>Three things live here (PRD §4.10): the display name, the password, and
 * whether the email address has been verified. The email address itself is
 * shown but not editable — changing it would mean confirming a new address
 * and invalidating the old one, which is more than the PRD asks for.
 *
 * <p>The password form is only offered to an account that has a password. A
 * Google-only account has none, so there is nothing to change and the form
 * would be a lie.
 */
export default function SettingsPage() {
  const { customer, refresh } = useCustomer()

  const [displayName, setDisplayName] = useState('')
  const [savingName, setSavingName] = useState(false)
  const [nameNotice, setNameNotice] = useState<string | null>(null)

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [savingPassword, setSavingPassword] = useState(false)
  const [passwordNotice, setPasswordNotice] = useState<string | null>(null)
  const [passwordError, setPasswordError] = useState<string | null>(null)

  const [error, setError] = useState<string | null>(null)

  // The name field starts empty and fills in once the account arrives, so
  // typing in it is never overwritten by a re-render.
  useEffect(() => {
    if (customer) {
      setDisplayName(customer.displayName)
    }
  }, [customer])

  if (!customer) {
    return (
      <p role="status" aria-live="polite" className="text-sm text-slate-600">
        Loading your account…
      </p>
    )
  }

  const hasPassword = customer.hasPassword

  const handleSaveName = async () => {
    setSavingName(true)
    setError(null)
    setNameNotice(null)
    try {
      await updateProfile(displayName.trim())
      await refresh()
      setNameNotice('Your name has been updated.')
    } catch (cause) {
      setError(describeFailure(cause, 'Your name could not be updated.'))
    } finally {
      setSavingName(false)
    }
  }

  const handleChangePassword = async () => {
    if (newPassword !== confirmPassword) {
      setPasswordError('The two passwords do not match.')
      return
    }

    setSavingPassword(true)
    setError(null)
    setPasswordError(null)
    setPasswordNotice(null)
    try {
      await changePassword(currentPassword, newPassword)
      // The old password is deliberately left in the field: clearing it
      // would hide a typo, and the field is a password field, so nothing is
      // exposed by leaving it there.
      setNewPassword('')
      setConfirmPassword('')
      setPasswordNotice('Your password has been changed.')
    } catch (cause) {
      setPasswordError(
        describeFailure(cause, 'Your password could not be changed.'),
      )
    } finally {
      setSavingPassword(false)
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          Account settings
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Your name, your password and your email address.
        </p>
      </div>

      {error && <ErrorBanner message={error} onDismiss={() => setError(null)} />}

      <Card className="p-6">
        <h2 className="text-base font-semibold text-slate-900">
          Email address
        </h2>
        <p className="mt-1 text-sm text-slate-600">{customer.email}</p>

        {customer.emailVerified ? (
          <p
            role="status"
            className="mt-3 inline-block rounded-md bg-emerald-50 px-2 py-1 text-sm font-medium text-emerald-800"
          >
            Verified
          </p>
        ) : (
          <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2">
            <p className="text-sm text-amber-900">
              This address is not verified yet. Open the link we emailed you
              to finish setting up your account.
            </p>
          </div>
        )}
      </Card>

      <Card className="p-6">
        <h2 className="text-base font-semibold text-slate-900">
          Your name
        </h2>
        <p className="mt-1 text-sm text-slate-600">
          This is the name your customers see on the pages you share.
        </p>

        <div className="mt-4 max-w-sm">
          <Field label="Display name" required>
            {(props) => (
              <input
                {...props}
                type="text"
                value={displayName}
                maxLength={100}
                onChange={(event) => setDisplayName(event.target.value)}
                className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
              />
            )}
          </Field>
        </div>

        {nameNotice && (
          <p
            role="status"
            className="mt-3 text-sm font-medium text-emerald-700"
          >
            {nameNotice}
          </p>
        )}

        <div className="mt-4">
          <Button
            loading={savingName}
            disabled={displayName.trim().length === 0}
            onClick={() => void handleSaveName()}
          >
            Save name
          </Button>
        </div>
      </Card>

      {hasPassword ? (
        <Card className="p-6">
          <h2 className="text-base font-semibold text-slate-900">
            Password
          </h2>
          <p className="mt-1 text-sm text-slate-600">
            Choose a password of at least eight characters.
          </p>

          <div className="mt-4 grid max-w-md gap-4">
            <Field label="Current password" required>
              {(props) => (
                <input
                  {...props}
                  type="password"
                  value={currentPassword}
                  autoComplete="current-password"
                  onChange={(event) => setCurrentPassword(event.target.value)}
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              )}
            </Field>

            <Field label="New password" required>
              {(props) => (
                <input
                  {...props}
                  type="password"
                  value={newPassword}
                  autoComplete="new-password"
                  onChange={(event) => setNewPassword(event.target.value)}
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              )}
            </Field>

            <Field label="Confirm new password" required>
              {(props) => (
                <input
                  {...props}
                  type="password"
                  value={confirmPassword}
                  autoComplete="new-password"
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
                />
              )}
            </Field>
          </div>

          {passwordError && (
            <p role="alert" className="mt-3 text-sm font-medium text-rose-600">
              {passwordError}
            </p>
          )}

          {passwordNotice && (
            <p
              role="status"
              className="mt-3 text-sm font-medium text-emerald-700"
            >
              {passwordNotice}
            </p>
          )}

          <div className="mt-4">
            <Button
              loading={savingPassword}
              disabled={
                currentPassword.length === 0 ||
                newPassword.length === 0 ||
                confirmPassword.length === 0
              }
              onClick={() => void handleChangePassword()}
            >
              Change password
            </Button>
          </div>
        </Card>
      ) : (
        <Card className="p-6">
          <h2 className="text-base font-semibold text-slate-900">
            Password
          </h2>
          <p className="mt-1 text-sm text-slate-600">
            You sign in with Google, so this account has no password to
            change.
          </p>
        </Card>
      )}
    </div>
  )
}
