import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva('chip', {
  variants: {
    variant: {
      default: '',
      primary: 'chip-accent',
      success: 'chip-success',
      warning: 'chip-warning',
      danger: 'chip-danger',
      muted: '',
      accent: 'chip-accent',
    },
  },
  defaultVariants: { variant: 'default' },
});

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant, className }))} {...props} />;
}
