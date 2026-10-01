import { describe, expect, it } from 'vitest';
import {
  bucketsFromLogs,
  formatCompactNumber,
  formatCount,
  formatLatency,
  hasTrafficData,
  normalizeTrafficMetrics,
  sparklineGeometry,
  successRatePercent,
  TRAFFIC_BUCKET_COUNT,
} from './route-metrics';

describe('route-metrics', () => {
  it('formats compact traffic numbers for the card metric row', () => {
    expect(formatCompactNumber(0)).toBe('0');
    expect(formatCompactNumber(940)).toBe('940');
    expect(formatCompactNumber(12_400)).toBe('12.4k');
    expect(formatCompactNumber(1_284_000)).toBe('1.3M');
  });

  it('formats latency with ms and s units', () => {
    expect(formatLatency(18)).toBe('18ms');
    expect(formatLatency(1500)).toBe('1.5s');
  });

  it('formats request counts and success rate', () => {
    expect(formatCount(12482)).toBe('12,482');
    expect(successRatePercent(1000, 2)).toBe('99.8%');
    expect(successRatePercent(0, 0)).toBe('—');
  });

  it('normalizes missing backend metrics into a safe shape', () => {
    const metrics = normalizeTrafficMetrics('route-a', { totalRequests: 12, trafficBuckets: [1, 2] });

    expect(metrics.totalRequests).toBe(12);
    expect(metrics.requestsLastMinute).toBe(0);
    expect(metrics.trafficBuckets).toHaveLength(TRAFFIC_BUCKET_COUNT);
    expect(metrics.trafficBuckets.every((value) => value === 0)).toBe(true);
  });

  it('keeps a full-length backend series untouched', () => {
    const series = new Array<number>(TRAFFIC_BUCKET_COUNT).fill(3);
    const metrics = normalizeTrafficMetrics('route-a', { trafficBuckets: series });
    expect(metrics.trafficBuckets).toHaveLength(TRAFFIC_BUCKET_COUNT);
    expect(metrics.trafficBuckets[0]).toBe(3);
  });

  it('treats routes without traffic as having no data', () => {
    expect(hasTrafficData(null)).toBe(false);
    expect(hasTrafficData(normalizeTrafficMetrics('route-a', { totalRequests: 0 }))).toBe(false);
    expect(hasTrafficData(normalizeTrafficMetrics('route-a', { totalRequests: 5 }))).toBe(true);
  });

  it('builds a flat sparkline baseline when there is no traffic', () => {
    const geometry = sparklineGeometry(new Array<number>(TRAFFIC_BUCKET_COUNT).fill(0), 240, 30);

    expect(geometry.hasSignal).toBe(false);
    expect(geometry.peak).toBe(0);
    expect(geometry.line.startsWith('M0 ')).toBe(true);
    expect(geometry.line.split('L')).toHaveLength(TRAFFIC_BUCKET_COUNT);
    expect(geometry.area.endsWith('Z')).toBe(true);
  });

  it('scales the sparkline peak to the chart height', () => {
    const geometry = sparklineGeometry([0, 5, 10], 240, 30);

    expect(geometry.hasSignal).toBe(true);
    expect(geometry.peak).toBe(10);
    // 峰值贴近顶部（height - 2 - 1 * (height - 6) = 4），零值贴近底部（height - 2 = 28）
    expect(geometry.line).toContain('L240 4');
    expect(geometry.line).toContain('L120 16');
    expect(geometry.line.startsWith('M0 28')).toBe(true);
  });

  it('falls back to a 30 point series built from recent logs', () => {
    const now = Date.now();
    const buckets = bucketsFromLogs([
      { timestamp: new Date(now).toISOString(), path: '/a' },
      { timestamp: new Date(now - 60_000).toISOString(), path: '/b' },
      { timestamp: new Date(now - 60 * 60 * 1000).toISOString(), path: '/old' },
    ]);

    expect(buckets).toHaveLength(TRAFFIC_BUCKET_COUNT);
    expect(buckets[TRAFFIC_BUCKET_COUNT - 1]).toBe(1);
    expect(buckets[TRAFFIC_BUCKET_COUNT - 2]).toBe(1);
  });
});
