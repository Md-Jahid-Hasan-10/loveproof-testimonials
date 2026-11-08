import { useContext } from 'react'
import { AdminSessionContext } from './adminSession'
import type { AdminSessionState } from './adminSession'

/**
 * Reads the admin session.
 *
 * @throws when called outside an `AuthProvider`
 */
export function useAuth(): AdminSessionState {
  const context = useContext(AdminSessionContext)
  if (context === undefined) {
    throw new Error('useAuth must be used inside an AuthProvider.')
  }
  return context
}
