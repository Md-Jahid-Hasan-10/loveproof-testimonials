import { createContext } from 'react'
import type { Customer } from '../api'

/**
 * The signed-in customer, or `null` when nobody is signed in.
 */
export interface CustomerSessionState {
  customer: Customer | null
  /** Whether the session is still being restored from the cookie. */
  loading: boolean
  /** The last authentication failure, when one happened. */
  error: string | null
  signIn: (email: string, password: string) => Promise<boolean>
  register: (
    email: string,
    password: string,
    displayName?: string | null,
  ) => Promise<boolean>
  signOut: () => Promise<void>
  /** Re-reads the session, for example after the server expired it. */
  refresh: () => Promise<void>
}

/**
 * The context holding the customer's session.
 *
 * It lives in its own module so the provider component and the hook can be
 * separate files, which keeps React Fast Refresh working.
 */
export const CustomerSessionContext = createContext<
  CustomerSessionState | undefined
>(undefined)
