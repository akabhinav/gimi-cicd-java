import { describe, it, expect } from 'vitest'
import { formatDuration, getStatusColor, getStatusBgColor, truncate, cn } from '@/lib/utils'

describe('formatDuration', () => {
  it('formats milliseconds', () => {
    expect(formatDuration(500)).toBe('500ms')
  })

  it('formats seconds', () => {
    expect(formatDuration(5000)).toBe('5s')
  })

  it('formats minutes and seconds', () => {
    expect(formatDuration(125000)).toBe('2m 5s')
  })

  it('formats hours and minutes', () => {
    expect(formatDuration(3725000)).toBe('1h 2m')
  })
})

describe('getStatusColor', () => {
  it('returns success color for success', () => {
    expect(getStatusColor('success')).toBe('text-status-success')
    expect(getStatusColor('succeeded')).toBe('text-status-success')
  })

  it('returns running color for running', () => {
    expect(getStatusColor('running')).toBe('text-status-running')
  })

  it('returns failed color for failed', () => {
    expect(getStatusColor('failed')).toBe('text-status-failed')
  })

  it('returns pending color for pending', () => {
    expect(getStatusColor('pending')).toBe('text-status-pending')
  })

  it('returns default for unknown status', () => {
    expect(getStatusColor('unknown')).toBe('text-text-secondary')
  })
})

describe('getStatusBgColor', () => {
  it('returns success bg classes', () => {
    expect(getStatusBgColor('success')).toContain('bg-status-success')
  })

  it('returns running bg classes', () => {
    expect(getStatusBgColor('running')).toContain('bg-status-running')
  })
})

describe('truncate', () => {
  it('does not truncate short strings', () => {
    expect(truncate('hello', 10)).toBe('hello')
  })

  it('truncates long strings', () => {
    expect(truncate('hello world', 5)).toBe('hello...')
  })
})

describe('cn', () => {
  it('merges class names', () => {
    expect(cn('foo', 'bar')).toBe('foo bar')
  })

  it('handles conditional classes', () => {
    expect(cn('base', false && 'hidden', 'visible')).toBe('base visible')
  })
})
