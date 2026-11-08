import { useContext } from 'react'
import { CustomerSessionContext } from './customerSession'
import type { CustomerSessionState } from './customerSession'

/**
 * Reads the customer session.
 *
 * @throws when called outside a `CustomerAuthProvider`
 */
export function useCustomer(): CustomerSessionState {
  const context = useContext(CustomerSessionContext)
  if (context === undefined) {
    throw new Error('useCustomer must be used inside a CustomerAuthProvider.')
  }
  return context
}
