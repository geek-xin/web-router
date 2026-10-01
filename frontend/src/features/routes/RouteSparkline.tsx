import * as React from 'react';
import { sparklineGeometry, TRAFFIC_BUCKET_COUNT } from './route-metrics';

interface RouteSparklineProps {
  values?: number[] | null;
  /** 停用路由：曲线静止并降权，见设计系统 §5.4。 */
  idle?: boolean;
  label?: string;
  className?: string;
}

const WIDTH = 240;
const HEIGHT = 30;

export function RouteSparkline({ values, idle = false, label, className }: RouteSparklineProps) {
  const gradientId = React.useId();
  const series = values && values.length > 0 ? values : new Array<number>(TRAFFIC_BUCKET_COUNT).fill(0);
  const geometry = React.useMemo(() => sparklineGeometry(series, WIDTH, HEIGHT), [series]);

  return (
    <svg
      className={['sparkline', idle || !geometry.hasSignal ? 'sparkline-idle' : '', className].filter(Boolean).join(' ')}
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={label || '最近 30 分钟请求数'}
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.28" />
          <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path className="sparkline-area" d={geometry.area} fill={`url(#${gradientId})`} />
      <path className="sparkline-line" d={geometry.line} />
    </svg>
  );
}
