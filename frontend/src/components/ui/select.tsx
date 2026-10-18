import * as React from 'react';
import * as SelectPrimitive from '@radix-ui/react-select';
import { Check, ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * 统一的下拉选择器。
 *
 * <p>为什么不用原生 {@code <select>}：原生下拉的**展开列表由操作系统绘制**，
 * CSS 无法作用于它（只对触发框生效）。结果是同一套界面里出现两种视觉语言——
 * 触发框是设计系统的白底圆角，展开列表却是系统灰底直角，
 * 深浅色主题也不跟随。改用 Radix Select 后，触发框与弹层都走同一套令牌。</p>
 *
 * <p>对外 API 从原生事件模型（{@code onChange(event.target.value)}）
 * 改为值模型（{@code onValueChange(value)}），因为弹层不是表单控件，
 * 无法承载原生 change 事件。调用点需相应调整。</p>
 */

export interface SelectOption {
  /** 选项值；空字符串表示「全部」这类无筛选语义。 */
  value: string;
  /** 展示文案。 */
  label: string;
  /** 可选：展示在右侧的次要说明（等宽）。 */
  hint?: string;
  disabled?: boolean;
}

interface SelectProps {
  value: string;
  onValueChange: (value: string) => void;
  options: SelectOption[];
  /** 无障碍标签；弹层与触发框共用。 */
  'aria-label'?: string;
  /** 占位文案，仅在没有匹配选项时显示。 */
  placeholder?: string;
  className?: string;
  /** 触发框宽度；默认按内容自适应。 */
  width?: number | string;
  disabled?: boolean;
  id?: string;
}

export function Select({ value, onValueChange, options, placeholder = '请选择', className, width, disabled, id, ...aria }: SelectProps) {
  const selected = options.find((option) => option.value === value);
  return (
    <SelectPrimitive.Root value={value} onValueChange={onValueChange} disabled={disabled}>
      <SelectPrimitive.Trigger
        id={id}
        className={cn('select-trigger', className)}
        style={width === undefined ? undefined : { width: typeof width === 'number' ? width + 'px' : width }}
        aria-label={aria['aria-label']}
      >
        <span className="select-value">
          {selected ? (
            <>
              {selected.label}
              {selected.hint && <span className="select-hint">{selected.hint}</span>}
            </>
          ) : (
            <span className="select-placeholder">{placeholder}</span>
          )}
        </span>
        <SelectPrimitive.Icon asChild>
          <ChevronDown className="select-caret" aria-hidden="true" />
        </SelectPrimitive.Icon>
      </SelectPrimitive.Trigger>

      <SelectPrimitive.Portal>
        <SelectPrimitive.Content className="select-content" position="popper" sideOffset={6} collisionPadding={10}>
          <SelectPrimitive.Viewport className="select-viewport">
            {options.map((option) => (
              <SelectPrimitive.Item key={option.value} value={option.value} disabled={option.disabled} className="select-item">
                {/*
                  Radix 的 ItemText 会把 className 解构掉且不落到 DOM 上（见其实现：
                  `const { __scopeSelect, className, style, ...itemTextProps } = props`，
                  className 只被解构、从未使用），所以间距样式放在内层 span 上。
                */}
                <SelectPrimitive.ItemText>
                  <span className="select-item-text">
                    <span className="select-item-label">{option.label}</span>
                    {option.hint && <span className="select-item-hint">{option.hint}</span>}
                  </span>
                </SelectPrimitive.ItemText>
                <SelectPrimitive.ItemIndicator className="select-item-check">
                  <Check className="h-3.5 w-3.5" aria-hidden="true" />
                </SelectPrimitive.ItemIndicator>
              </SelectPrimitive.Item>
            ))}
          </SelectPrimitive.Viewport>
        </SelectPrimitive.Content>
      </SelectPrimitive.Portal>
    </SelectPrimitive.Root>
  );
}
