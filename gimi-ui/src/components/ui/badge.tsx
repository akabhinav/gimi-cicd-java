import { cn, getStatusBgColor } from '@/lib/utils'

interface BadgeProps {
  children: React.ReactNode
  variant?: 'default' | 'status' | 'outline'
  status?: string
  className?: string
}

export function Badge({ children, variant = 'default', status, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium border',
        variant === 'status' && status ? getStatusBgColor(status) : '',
        variant === 'default' && 'bg-primary-500/15 text-primary-400 border-primary-500/30',
        variant === 'outline' && 'bg-transparent text-text-secondary border-border-primary',
        className
      )}
    >
      {children}
    </span>
  )
}

export function StatusBadge({ status }: { status: string }) {
  return (
    <Badge variant="status" status={status} className="capitalize">
      <span className={cn(
        'mr-1.5 h-1.5 w-1.5 rounded-full',
        status?.toLowerCase() === 'running' || status?.toLowerCase() === 'in_progress'
          ? 'animate-pulse bg-current'
          : 'bg-current'
      )} />
      {status?.toLowerCase().replace('_', ' ')}
    </Badge>
  )
}
