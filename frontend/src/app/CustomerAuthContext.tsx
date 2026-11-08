import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  ApiError,
  describeFailure,
  getCurrentCustomer,
  registerCustomer,
  signInCustomer,
  signOutCustomer,
} from '../api'
import type { Customer } from '../api'
import { CustomerSessionContext } from './customerSession'
import type { CustomerSessionState } from './customerSession'

/**
 * Holds the customer's session.
 *
 * On mount it asks the backend who the session cookie belongs to. The answer
 * is authoritative: the interface never decides on its own whether somebody is
 * signed in, because a stale cookie must not grant access to the dashboard.
 */
export function CustomerAuthProvider({ children }: { children: ReactNode }) {
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const current = await getCurrentCustomer()
      setCustomer(current)
      setError(null)
    } catch (cause) {
      // A 401 simply means nobody is signed in. It is not a failure worth
      // showing a banner for.
      if (cause instanceof ApiError && cause.status === 401) {
        setCustomer(null)
        setError(null)
      } else {
        setCustomer(null)
        setError(describeFailure(cause, 'The session could not be restored.'))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const handleSignIn = useCallback(
    async (email: string, password: string) => {
      setError(null)
      try {
        const signedIn = await signInCustomer(email, password)
        setCustomer(signedIn)
        return true
      } catch (cause) {
        // The sign-in failure message must reach the visitor word for word:
        // it is the same message for a wrong password and an unknown address
        // on purpose, so the page cannot reveal which addresses are
        // registered.
        setError(describeFailure(cause, 'The sign-in failed.', false))
        return false
      }
    },
    [],
  )

  const handleRegister = useCallback(
    async (email: string, password: string, displayName?: string | null) => {
      setError(null)
      try {
        const created = await registerCustomer({
          email,
          password,
          displayName: displayName ?? null,
        })
        setCustomer(created)
        return true
      } catch (cause) {
        setError(describeFailure(cause, 'The registration failed.', false))
        return false
      }
    },
    [],
  )

  const handleSignOut = useCallback(async () => {
    try {
      await signOutCustomer()
    } finally {
      // The session is gone either way. If the call failed, the local state
      // must not keep pretending that somebody is still signed in.
      setCustomer(null)
    }
  }, [])

  const value = useMemo<CustomerSessionState>(
    () => ({
      customer,
      loading,
      error,
      signIn: handleSignIn,
      register: handleRegister,
      signOut: handleSignOut,
      refresh,
    }),
    [customer, loading, error, handleSignIn, handleRegister, handleSignOut, refresh],
  )

  return (
    <CustomerSessionContext.Provider value={value}>
      {children}
    </CustomerSessionContext.Provider>
  )
}
