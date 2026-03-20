import { forwardRef } from 'react'
import { cn } from '@/lib/utils'
import { Slot } from '@radix-ui/react-slot'

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline'
  size?: 'sm' | 'md' | 'lg' | 'icon'
  asChild?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', asChild, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button'
    return (
      <Comp
        ref={ref}
        className={cn(
          'inline-flex items-center justify-center rounded-md font-medium transition-colors',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500/50',
          'disabled:pointer-events-none disabled:opacity-50',
          variant === 'primary' && 'bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700',
          variant === 'secondary' && 'bg-bg-tertiary text-text-primary border border-border-primary hover:bg-bg-hover',
          variant === 'ghost' && 'text-text-secondary hover:bg-bg-hover hover:text-text-primary',
          variant === 'danger' && 'bg-danger/10 text-danger border border-danger/30 hover:bg-danger/20',
          variant === 'outline' && 'border border-border-primary text-text-secondary hover:bg-bg-hover hover:text-text-primary',
          size === 'sm' && 'h-7 px-3 text-xs gap-1.5',
          size === 'md' && 'h-9 px-4 text-sm gap-2',
          size === 'lg' && 'h-11 px-6 text-base gap-2',
          size === 'icon' && 'h-9 w-9',
          className
        )}
        {...props}
      />
    )
  }
)
Button.displayName = 'Button'
