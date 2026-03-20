import { Search, Bell, HelpCircle, Plus, User, LogOut } from 'lucide-react'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { useLocation } from 'react-router-dom'

const pageTitles: Record<string, string> = {
  '/': 'Dashboard',
  '/pipelines': 'Pipelines',
  '/executions': 'Executions',
  '/workers': 'Delegates',
  '/analytics': 'DORA Metrics',
  '/slos': 'SLOs',
  '/security': 'Security Tests',
  '/feature-flags': 'Feature Flags',
  '/audit': 'Audit Trail',
  '/connectors': 'Connectors',
  '/templates': 'Templates',
  '/notifications': 'Notifications',
  '/governance': 'Governance',
  '/settings': 'Settings',
}

interface TopBarProps {
  onLogout?: () => void
}

export function TopBar({ onLogout }: TopBarProps) {
  const location = useLocation()
  const pathBase = '/' + (location.pathname.split('/')[1] || '')
  const title = pageTitles[pathBase] || 'GIMI'

  return (
    <header className="flex items-center justify-between h-14 px-6 border-b border-border-primary bg-bg-secondary">
      <div className="flex items-center gap-4">
        <h1 className="text-base font-semibold text-text-primary">{title}</h1>
        <div className="h-5 w-px bg-border-primary" />
        <nav className="flex items-center gap-1 text-xs text-text-tertiary">
          <span>Project</span>
          <span>/</span>
          <span className="text-text-secondary">{title}</span>
        </nav>
      </div>

      <div className="flex items-center gap-3">
        <div className="w-64">
          <Input
            placeholder="Search pipelines, runs..."
            icon={<Search className="h-3.5 w-3.5" />}
            className="h-8 text-xs"
          />
        </div>

        <Button variant="primary" size="sm">
          <Plus className="h-3.5 w-3.5" />
          Create
        </Button>

        <div className="h-5 w-px bg-border-primary" />

        <button className="p-1.5 text-text-tertiary hover:text-text-secondary transition-colors relative">
          <Bell className="h-4 w-4" />
          <span className="absolute top-0.5 right-0.5 h-2 w-2 rounded-full bg-danger" />
        </button>

        <button className="p-1.5 text-text-tertiary hover:text-text-secondary transition-colors">
          <HelpCircle className="h-4 w-4" />
        </button>

        <div className="h-8 w-8 rounded-full bg-primary-500/20 border border-primary-500/30 flex items-center justify-center">
          <User className="h-4 w-4 text-primary-400" />
        </div>

        {onLogout && (
          <button
            onClick={onLogout}
            className="p-1.5 text-text-tertiary hover:text-status-failed transition-colors"
            title="Sign out"
          >
            <LogOut className="h-4 w-4" />
          </button>
        )}
      </div>
    </header>
  )
}
