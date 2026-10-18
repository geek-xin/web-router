import * as React from 'react';
import { cn } from '@/lib/utils';

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) => (
  <input ref={ref} className={cn('field-input', className)} {...props} />
));
Input.displayName = 'Input';

// 下拉见 @/components/ui/select（Radix Select）：
// 原生 <select> 的展开列表由系统绘制，无法跟随主题与设计令牌。
