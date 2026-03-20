import { forwardRef } from 'react'
import { cn } from '@/lib/utils'

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  icon?: React.ReactNode
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ className, icon, ...props }, ref) => {
    return (
      <div className="relative">
        {icon && (
          <div className="absolute left-3 top-1/2 -translate-y-1/2 text-text-tertiary">
            {icon}
          </div>
        )}
        <input
          ref={ref}
          className={cn(
            'flex h-9 w-full rounded-md border border-border-primary bg-bg-secondary px-3 py-1',
            'text-sm text-text-primary placeholder:text-text-tertiary',
            'focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-500',
            'disabled:cursor-not-allowed disabled:opacity-50',
            icon && 'pl-9',
            className
          )}
          {...props}
        />
      </div>
    )
  }
)
Input.displayName = 'Input'
