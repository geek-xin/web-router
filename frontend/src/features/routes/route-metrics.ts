import type { ProxyRequestLogEntry } from '@/features/logs/types';

/** 与后端 RouteTrafficMetrics / ProxyRequestLogSnapshot 对应的紧凑流量指标。 */
export interface RouteTrafficMetrics {
  routeId: string;
  totalRequests: number;
  failedRequests: number;
  slowRequests: number;
  totalDurationMs: number;
  requestsLastMinute: number;
  failedLastMinute: number;
  averageDurationMs: number;
  trafficBuckets: number[];
}

import { TRAFFIC_WINDOW_MINUTES } from '@/lib/app-meta';

export const TRAFFIC_BUCKET_COUNT = TRAFFIC_WINDOW_MINUTES;

export function emptyTrafficBuckets(): number[] {
  return new Array<number>(TRAFFIC_BUCKET_COUNT).fill(0);
}

export function normalizeTrafficMetrics(routeId: string, raw?: Partial<RouteTrafficMetrics> | null): RouteTrafficMetrics {
  const buckets = Array.isArray(raw?.trafficBuckets) ? raw!.trafficBuckets.map((value) => Number(value) || 0) : [];
  return {
    routeId,
    totalRequests: numberOrZero(raw?.totalRequests),
    failedRequests: numberOrZero(raw?.failedRequests),
    slowRequests: numberOrZero(raw?.slowRequests),
    totalDurationMs: numberOrZero(raw?.totalDurationMs),
    requestsLastMinute: numberOrZero(raw?.requestsLastMinute),
    failedLastMinute: numberOrZero(raw?.failedLastMinute),
    averageDurationMs: numberOrZero(raw?.averageDurationMs),
    trafficBuckets: buckets.length === TRAFFIC_BUCKET_COUNT ? buckets : emptyTrafficBuckets(),
  };
}

/** 列表接口缺字段时返回 null，界面统一降级为 "-"。 */
export function hasTrafficData(metrics?: RouteTrafficMetrics | null): boolean {
  return Boolean(metrics && metrics.totalRequests > 0);
}

export function formatCompactNumber(value?: number | null): string {
  const number = numberOrZero(value);
  if (number < 1000) {
    return String(number);
  }
  if (number < 1_000_000) {
    const scaled = number / 1000;
    return (scaled >= 100 ? Math.round(scaled).toString() : scaled.toFixed(1).replace(/\.0$/, '')) + 'k';
  }
  const scaled = number / 1_000_000;
  return (scaled >= 100 ? Math.round(scaled).toString() : scaled.toFixed(1).replace(/\.0$/, '')) + 'M';
}

export function formatLatency(value?: number | null): string {
  const number = numberOrZero(value);
  if (number >= 1000) {
    return (number / 1000).toFixed(number >= 10_000 ? 0 : 1) + 's';
  }
  return number + 'ms';
}

export function formatCount(value?: number | null): string {
  return numberOrZero(value).toLocaleString('en-US');
}

export function successRatePercent(total?: number | null, failed?: number | null): string {
  const totalValue = numberOrZero(total);
  if (totalValue <= 0) {
    return '—';
  }
  const failedValue = Math.min(totalValue, numberOrZero(failed));
  const rate = ((totalValue - failedValue) / totalValue) * 100;
  return rate.toFixed(1) + '%';
}

export function failureRatePercent(total?: number | null, failed?: number | null): number {
  const totalValue = numberOrZero(total);
  if (totalValue <= 0) {
    return 0;
  }
  return (Math.min(totalValue, numberOrZero(failed)) / totalValue) * 100;
}

/** 把最近日志折算成 30 点分钟桶，仅在后端未提供 trafficBuckets 时兜底。 */
export function bucketsFromLogs(logs: ProxyRequestLogEntry[], bucketCount = TRAFFIC_BUCKET_COUNT): number[] {
  const buckets = new Array<number>(bucketCount).fill(0);
  const now = Date.now();
  for (const entry of logs) {
    const raw = entry.timestamp || entry.time;
    if (!raw) {
      continue;
    }
    const time = new Date(raw).getTime();
    if (Number.isNaN(time)) {
      continue;
    }
    const minutesAgo = Math.floor((now - time) / 60_000);
    if (minutesAgo < 0 || minutesAgo >= bucketCount) {
      continue;
    }
    buckets[bucketCount - 1 - minutesAgo] += 1;
  }
  return buckets;
}

export interface SparklineGeometry {
  line: string;
  area: string;
  peak: number;
  hasSignal: boolean;
}

/** 生成 Sparkline 的 SVG path，纯函数便于测试。 */
export function sparklineGeometry(values: number[], width = 240, height = 30): SparklineGeometry {
  const points = values.length > 0 ? values : [0];
  const peak = Math.max(...points);
  const ceiling = peak > 0 ? peak : 1;
  const stepX = points.length > 1 ? width / (points.length - 1) : 0;
  const coordinates = points.map((value, index) => {
    const ratio = value / ceiling;
    const x = Number((index * stepX).toFixed(3));
    const y = Number((height - 2 - ratio * (height - 6)).toFixed(3));
    return { x, y };
  });
  const line = coordinates.map((point, index) => (index === 0 ? 'M' : 'L') + point.x + ' ' + point.y).join(' ');
  const area = coordinates.length > 0
    ? line + ' L' + coordinates[coordinates.length - 1].x + ' ' + height + ' L' + coordinates[0].x + ' ' + height + ' Z'
    : '';
  return { line, area, peak, hasSignal: peak > 0 };
}

function numberOrZero(value?: number | null): number {
  const number = Number(value ?? 0);
  return Number.isFinite(number) && number > 0 ? number : 0;
}
