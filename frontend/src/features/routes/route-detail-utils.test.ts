import { describe, expect, it } from 'vitest';
import type { RouteConfig } from './types';
import { buildRouteTopologyModel, formatJsonContent, routeDetailMetrics } from './route-detail-utils';
import { normalizeTrafficMetrics } from './route-metrics';

const enabledRoute: RouteConfig = {
  id: 'route-demo',
  name: '演示路由',
  pathPrefix: '/legacy',
  pathPrefixes: ['/api', '/portal'],
  targetUrl: 'http://127.0.0.1:8080',
  accessPageBaseUrl: 'http://127.0.0.1:9999',
  accessPage: '/portal/login.html',
  localIp: '127.0.0.1',
  localPort: 9191,
  enabled: true,
};

const formValues = {
  name: '演示路由',
  targetUrl: '127.0.0.1:8080',
  accessPageBaseUrl: '127.0.0.1:9999',
  accessPage: '',
  localIp: '127.0.0.1',
  localPort: '9191',
  pathPrefixes: ['/portal', '/api', '/assets'],
  enabled: true,
};

describe('route-detail-utils', () => {
  it('formats valid JSON and preserves invalid JSON for editing', () => {
    expect(formatJsonContent('{"name":"A","enabled":true}')).toBe('{\n  "name": "A",\n  "enabled": true\n}');
    expect(formatJsonContent('{bad json')).toBe('{bad json');
  });

  it('builds the four-segment topology in the fixed brand order', () => {
    const model = buildRouteTopologyModel(formValues);

    // Client → wrouter → 本地端口 → 目标服务
    expect(model.client.kicker).toBe('Client');
    expect(model.gateway.kicker).toBe('wrouter');
    expect(model.localPort.kicker).toBe('本地端口');
    expect(model.target.kicker).toBe('目标服务（代理地址）');
    expect(model.localPort.value).toBe('127.0.0.1:9191');
    expect(model.target.value).toBe('127.0.0.1:9999');
    expect(model.gateway.value).toBe('/portal  /api  /assets');
    expect(model.active).toBe(true);
  });

  it('folds extra prefixes and keeps the fallback branch when prefixes exist', () => {
    const model = buildRouteTopologyModel({ ...formValues, pathPrefixes: ['/a', '/b', '/c', '/d'] });

    expect(model.prefixChips).toEqual(['/a', '/b', '/c']);
    expect(model.morePrefixes).toBe(1);
    expect(model.fallback).not.toBeNull();
    expect(model.fallback?.value).toBe('127.0.0.1:8080');
  });

  it('marks empty-prefix topology as fallback-only without a fallback branch node', () => {
    const model = buildRouteTopologyModel({ ...formValues, pathPrefixes: [], accessPageBaseUrl: '' });

    expect(model.gateway.value).toBe('全部兜底');
    expect(model.gateway.note).toBe('未配置前缀 → 直接兜底');
    expect(model.target.kicker).toBe('目标服务（默认地址）');
    expect(model.fallback).toBeNull();
  });

  it('reports runtime metrics with em dash placeholders when the backend has no data', () => {
    const metrics = routeDetailMetrics(enabledRoute, null);
    expect(metrics).toEqual([
      { label: '请求数', value: '—' },
      { label: '成功率', value: '—' },
      { label: '平均延迟', value: '—' },
      { label: '路径前缀', value: '2' },
      { label: '监听地址', value: '127.0.0.1:9191' },
      { label: '默认地址', value: '127.0.0.1:8080' },
    ]);
  });

  it('reports runtime metrics from traffic counters', () => {
    const metrics = normalizeTrafficMetrics('route-demo', {
      totalRequests: 1000,
      failedRequests: 2,
      averageDurationMs: 18,
    });

    expect(routeDetailMetrics(enabledRoute, metrics)).toContainEqual({ label: '请求数', value: '1000' });
    expect(routeDetailMetrics(enabledRoute, metrics)).toContainEqual({ label: '成功率', value: '99.8%' });
    expect(routeDetailMetrics(enabledRoute, metrics)).toContainEqual({ label: '平均延迟', value: '18ms' });
  });
});
