import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { ApiError, describeFailure, getSession, signIn, signOut } from '../api'
import { AdminSessionContext } from './adminSession'
import type { AdminSessionLike, AdminSessionState } from './adminSession'

/**
 * Holds the administrator's session.
 *
 * On mount it asks the backend who the session cookie belongs to. The answer
 * is authoritative: the UI never decides on its own whether somebody is
 * signed in, because a stale cookie must not grant access to the admin
 * screens.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [admin, setAdmin] = useState<AdminSessionLike | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const session = await getSession()
      setAdmin(session)
      setError(null)
    } catch (cause) {
      // A 401 simply means nobody is signed in. It is not a failure worth
      // showing a banner for.
      if (cause instanceof ApiError && cause.status === 401) {
        setAdmin(null)
        setError(null)
      } else {
        setAdmin(null)
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
    async (username: string, password: string) => {
      setError(null)
      try {
        const session = await signIn(username, password)
        setAdmin(session)
        return true
      } catch (cause) {
        // The sign-in endpoint's 401 is the deliberate "invalid username or
        // password", which must reach the visitor word for word: it is what
        // keeps the page from revealing which half of the credentials was
        // wrong. So a 401 is not reworded here.
        setError(describeFailure(cause, 'The sign-in failed.', false))
        return false
      }
    },
    [],
  )

  const handleSignOut = useCallback(async () => {
    try {
      await signOut()
    } finally {
      // The session is gone either way. If the call failed, the local state
      // must not keep pretending that somebody is still signed in.
      setAdmin(null)
    }
  }, [])

  const value = useMemo<AdminSessionState>(
    () => ({
      admin,
      loading,
      error,
      signIn: handleSignIn,
      signOut: handleSignOut,
      refresh,
    }),
    [admin, loading, error, handleSignIn, handleSignOut, refresh],
  )

  return (
    <AdminSessionContext.Provider value={value}>
      {children}
    </AdminSessionContext.Provider>
  )
}
