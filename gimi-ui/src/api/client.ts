const BASE_URL = '/api'

export class AuthError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'AuthError'
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('gimi_token')
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  })
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('gimi_token')
    window.dispatchEvent(new CustomEvent('auth:logout'))
    throw new AuthError('Session expired. Please log in again.')
  }
  if (!res.ok) {
    const error = await res.text().catch(() => res.statusText)
    throw new Error(error || `Request failed: ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export async function login(username: string, password: string): Promise<{ token: string }> {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  if (!res.ok) {
    const error = await res.text().catch(() => 'Login failed')
    throw new Error(error)
  }
  const data = await res.json()
  localStorage.setItem('gimi_token', data.token)
  return data
}

export function logout() {
  localStorage.removeItem('gimi_token')
  window.dispatchEvent(new CustomEvent('auth:logout'))
}

export function isAuthenticated(): boolean {
  return !!localStorage.getItem('gimi_token')
}
