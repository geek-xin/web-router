import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva('console-badge', {
  variants: {
    variant: {
      default: '',
      primary: 'console-badge-primary',
      success: 'console-badge-success',
      warning: 'console-badge-warning',
      danger: 'console-badge-danger',
      muted: 'console-badge-muted',
      accent: 'console-badge-primary',
    },
  },
  defaultVariants: { variant: 'default' },
});

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant, className }))} {...props} />;
}
