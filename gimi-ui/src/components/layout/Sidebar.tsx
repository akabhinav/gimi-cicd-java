import { NavLink, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import {
  LayoutDashboard, GitBranch, Play, Server, Shield, Flag,
  Settings, BarChart3, FileText, Activity, ChevronLeft, ChevronRight,
  Plug, Bell, BookTemplate, Scale, ClipboardList, Layers, Cloud
} from 'lucide-react'
import { useState } from 'react'

const navSections = [
  {
    label: 'Overview',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
      { to: '/pipelines', icon: GitBranch, label: 'Pipelines' },
      { to: '/executions', icon: Play, label: 'Executions' },
    ],
  },
  {
    label: 'Operations',
    items: [
      { to: '/workers', icon: Server, label: 'Delegates' },
      { to: '/analytics', icon: BarChart3, label: 'DORA Metrics' },
      { to: '/slos', icon: Activity, label: 'SLOs' },
    ],
  },
  {
    label: 'Security',
    items: [
      { to: '/security', icon: Shield, label: 'Security Tests' },
      { to: '/feature-flags', icon: Flag, label: 'Feature Flags' },
      { to: '/audit', icon: ClipboardList, label: 'Audit Trail' },
    ],
  },
  {
    label: 'Infrastructure',
    items: [
      { to: '/iac', icon: Layers, label: 'IaC Management' },
      { to: '/saas', icon: Cloud, label: 'SaaS Hosting' },
    ],
  },
  {
    label: 'Configuration',
    items: [
      { to: '/connectors', icon: Plug, label: 'Connectors' },
      { to: '/templates', icon: BookTemplate, label: 'Templates' },
      { to: '/notifications', icon: Bell, label: 'Notifications' },
      { to: '/governance', icon: Scale, label: 'Governance' },
      { to: '/settings', icon: Settings, label: 'Settings' },
    ],
  },
]

export function Sidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()

  return (
    <aside
      className={cn(
        'flex flex-col h-screen bg-bg-secondary border-r border-border-primary transition-all duration-200',
        collapsed ? 'w-16' : 'w-60'
      )}
    >
      {/* Logo */}
      <div className="flex items-center h-14 px-4 border-b border-border-primary">
        <div className="flex items-center gap-2.5">
          <div className="h-8 w-8 rounded-lg bg-primary-500 flex items-center justify-center text-white font-bold text-sm flex-shrink-0">
            G
          </div>
          {!collapsed && (
            <div>
              <div className="text-sm font-semibold text-text-primary">GIMI</div>
              <div className="text-[10px] text-text-tertiary">CI/CD Platform</div>
            </div>
          )}
        </div>
      </div>

      {/* Project selector */}
      {!collapsed && (
        <div className="px-3 py-3 border-b border-border-primary">
          <div className="flex items-center gap-2 px-2 py-1.5 rounded-md bg-bg-tertiary border border-border-primary text-xs">
            <FileText className="h-3.5 w-3.5 text-primary-400" />
            <span className="text-text-primary font-medium">Default Project</span>
          </div>
        </div>
      )}

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-2">
        {navSections.map((section) => (
          <div key={section.label} className="mb-1">
            {!collapsed && (
              <div className="px-4 py-2 text-[10px] font-semibold text-text-tertiary uppercase tracking-wider">
                {section.label}
              </div>
            )}
            {section.items.map((item) => {
              const isActive = location.pathname === item.to ||
                (item.to !== '/' && location.pathname.startsWith(item.to))
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={cn(
                    'flex items-center gap-3 mx-2 px-3 py-2 rounded-md text-sm transition-colors',
                    isActive
                      ? 'bg-primary-500/10 text-primary-400 border-l-2 border-primary-400'
                      : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary border-l-2 border-transparent',
                    collapsed && 'justify-center px-2'
                  )}
                >
                  <item.icon className={cn('h-4 w-4 flex-shrink-0', isActive && 'text-primary-400')} />
                  {!collapsed && <span>{item.label}</span>}
                </NavLink>
              )
            })}
          </div>
        ))}
      </nav>

      {/* Collapse toggle */}
      <button
        onClick={() => setCollapsed(!collapsed)}
        className="flex items-center justify-center h-10 border-t border-border-primary text-text-tertiary hover:text-text-secondary hover:bg-bg-hover transition-colors"
      >
        {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
      </button>
    </aside>
  )
}
