import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { LogIn, AlertCircle } from 'lucide-react'

interface LoginPageProps {
  onLogin: (username: string, password: string) => Promise<void>
  loading: boolean
  error: string | null
}

export function LoginPage({ onLogin, loading, error }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username || !password) return
    try {
      await onLogin(username, password)
    } catch {
      // error is handled by parent
    }
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-bg-primary">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="flex flex-col items-center mb-8">
          <div className="h-14 w-14 rounded-2xl bg-primary-500 flex items-center justify-center text-white font-bold text-2xl mb-4">
            G
          </div>
          <h1 className="text-xl font-semibold text-text-primary">GIMI CI/CD</h1>
          <p className="text-sm text-text-tertiary mt-1">Sign in to your account</p>
        </div>

        <Card>
          <CardContent className="pt-6">
            <form onSubmit={handleSubmit} className="space-y-4">
              {error && (
                <div className="flex items-center gap-2 p-3 rounded-md bg-danger/10 border border-danger/30 text-danger text-sm">
                  <AlertCircle className="h-4 w-4 flex-shrink-0" />
                  <span>{error}</span>
                </div>
              )}

              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">Username</label>
                <Input
                  type="text"
                  placeholder="admin"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoFocus
                  autoComplete="username"
                />
              </div>

              <div>
                <label className="text-xs font-medium text-text-secondary block mb-1.5">Password</label>
                <Input
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                />
              </div>

              <Button type="submit" className="w-full" disabled={loading || !username || !password}>
                {loading ? (
                  <span className="flex items-center gap-2">
                    <span className="h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Signing in...
                  </span>
                ) : (
                  <>
                    <LogIn className="h-4 w-4" />
                    Sign In
                  </>
                )}
              </Button>
            </form>

            <div className="mt-4 pt-4 border-t border-border-primary">
              <p className="text-[11px] text-text-tertiary text-center">
                Default credentials: <span className="text-text-secondary font-mono">admin</span> / <span className="text-text-secondary font-mono">admin</span>
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
