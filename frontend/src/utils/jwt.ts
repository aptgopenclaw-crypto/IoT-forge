/**
 * Parse the expiration time (in seconds) from a JWT access token.
 * Returns `null` if the token is malformed or missing `exp`.
 */
export function getJwtExp(token: string): number | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp : null
  } catch {
    return null
  }
}

/**
 * Returns milliseconds until the JWT expires.
 * Returns `null` if `exp` cannot be parsed.
 */
export function getJwtRemainingMs(token: string): number | null {
  const exp = getJwtExp(token)
  if (exp === null) return null
  return exp * 1000 - Date.now()
}
