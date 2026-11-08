import { createContext } from 'react'

/**
 * The shape of the session the backend returns.
 */
export interface AdminSessionLike {
  username: string
  displayName: string
  role: string
  lastLoginAt: string | null
}

/**
 * The signed-in administrator, or `null` when nobody is signed in.
 */
export interface AdminSessionState {
  admin: AdminSessionLike | null
  /** Whether the session is still being restored from the cookie. */
  loading: boolean
  /** The last authentication failure, when one happened. */
  error: string | null
  signIn: (username: string, password: string) => Promise<boolean>
  signOut: () => Promise<void>
  /** Re-reads the session, for example after the server expired it. */
  refresh: () => Promise<void>
}

/**
 * The context holding the administrator's session.
 *
 * It lives in its own module so the provider component and the hook can be
 * separate files, which keeps React Fast Refresh working.
 */
export const AdminSessionContext = createContext<AdminSessionState | undefined>(
  undefined,
)
