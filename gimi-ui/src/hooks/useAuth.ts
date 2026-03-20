import { useState, useEffect, useCallback } from 'react'
import { login as apiLogin, logout as apiLogout, isAuthenticated } from '@/api/client'

export function useAuth() {
  const [authenticated, setAuthenticated] = useState(isAuthenticated())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const handler = () => setAuthenticated(false)
    window.addEventListener('auth:logout', handler)
    return () => window.removeEventListener('auth:logout', handler)
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    setLoading(true)
    setError(null)
    try {
      await apiLogin(username, password)
      setAuthenticated(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
      throw err
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(() => {
    apiLogout()
    setAuthenticated(false)
  }, [])

  return { authenticated, login, logout, loading, error }
}
