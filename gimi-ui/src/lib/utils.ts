import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSecs = seconds % 60
  if (minutes < 60) return `${minutes}m ${remainingSecs}s`
  const hours = Math.floor(minutes / 60)
  const remainingMins = minutes % 60
  return `${hours}h ${remainingMins}m`
}

export function formatDate(date: string | Date): string {
  const d = new Date(date)
  return d.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function getStatusColor(status: string): string {
  switch (status?.toLowerCase()) {
    case 'success':
    case 'succeeded':
    case 'completed':
      return 'text-status-success'
    case 'running':
    case 'in_progress':
      return 'text-status-running'
    case 'failed':
    case 'error':
      return 'text-status-failed'
    case 'pending':
    case 'waiting':
      return 'text-status-pending'
    case 'queued':
      return 'text-status-queued'
    default:
      return 'text-text-secondary'
  }
}

export function getStatusBgColor(status: string): string {
  switch (status?.toLowerCase()) {
    case 'success':
    case 'succeeded':
    case 'completed':
      return 'bg-status-success/15 text-status-success border-status-success/30'
    case 'running':
    case 'in_progress':
      return 'bg-status-running/15 text-status-running border-status-running/30'
    case 'failed':
    case 'error':
      return 'bg-status-failed/15 text-status-failed border-status-failed/30'
    case 'pending':
    case 'waiting':
      return 'bg-status-pending/15 text-status-pending border-status-pending/30'
    case 'queued':
      return 'bg-status-queued/15 text-status-queued border-status-queued/30'
    default:
      return 'bg-bg-hover text-text-secondary border-border-primary'
  }
}

export function truncate(str: string, length: number): string {
  if (str.length <= length) return str
  return str.slice(0, length) + '...'
}
