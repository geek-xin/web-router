import { describe, expect, it } from 'vitest';
import { activeLocalBinding, deriveRouteStatus, filterRoutes, normalizePathPrefix, routeAccentClass, routeAccessUrl, routeStatusDotClass, sortRoutes, validateRoutePayload, visiblePathPrefixes } from './route-utils';
import { normalizeTrafficMetrics } from './route-metrics';

describe('route route-utils', () => {
  it('normalizes path prefixes and removes trailing slashes', () => {
    expect(normalizePathPrefix('/api//')).toBe('/api');
  });

  it('maps route status to the dot classes defined in styles.css', () => {
    // 回归：这里曾经返回 status-*，而样式表只定义了 .state-*，导致状态点全部是灰色。
    // .status-* 是日志表 HTTP 状态码徽标的命名空间，不要与状态点混用。
    expect(routeStatusDotClass('running')).toBe('state-running');
    expect(routeStatusDotClass('stopped')).toBe('state-stopped');
    expect(routeStatusDotClass('warning')).toBe('state-warning');
    expect(routeStatusDotClass('error')).toBe('state-error');
  });

  it('opens relative access pages through active local binding', () => {
    expect(routeAccessUrl({ localBinding: '127.0.0.1:9191', accessPage: '/portal', pathPrefixes: ['/api'] })).toBe('http://127.0.0.1:9191/portal');
  });

  it('requires proxy address when path prefixes exist', () => {
    expect(validateRoutePayload({ name: 'A', targetUrl: '127.0.0.1:8080', localIp: '127.0.0.1', localPort: '9191', accessPageBaseUrl: '', accessPage: '', pathPrefixes: ['/api'] }).errors).toContain('配置路径前缀时请输入代理地址');
  });


  it('allows disabled copied routes to keep an existing listener binding', () => {
    const result = validateRoutePayload(
      { name: 'A-copy', targetUrl: '127.0.0.1:8080', localIp: '127.0.0.1', localPort: '9191', accessPageBaseUrl: '', accessPage: '', pathPrefixes: [], enabled: false },
      [],
      ['127.0.0.1:9191'],
    );

    expect(result.errors).not.toContain('监听地址已被其他启用路由使用');
    expect(result.payload?.enabled).toBe(false);
  });

  it('rejects listener binding conflicts when enabling a route', () => {
    expect(validateRoutePayload(
      { name: 'A', targetUrl: '127.0.0.1:8080', localIp: '127.0.0.1', localPort: '9191', accessPageBaseUrl: '', accessPage: '', pathPrefixes: [], enabled: true },
      [],
      ['127.0.0.1:9191'],
    ).errors).toContain('监听地址已被其他启用路由使用');
  });

  it('only reports existing local bindings for enabled routes', () => {
    expect(activeLocalBinding({ id: 'disabled', name: '停用', targetUrl: 'http://127.0.0.1:8080', localIp: '127.0.0.1', localPort: 9191, enabled: false })).toBe('');
    expect(activeLocalBinding({ id: 'enabled', name: '启用', targetUrl: 'http://127.0.0.1:8080', localIp: '127.0.0.1', localPort: 9191, enabled: true })).toBe('127.0.0.1:9191');
  });
  it('assigns a stable accent per route position', () => {
    expect(routeAccentClass(0)).toBe('accent-blue');
    expect(routeAccentClass(3)).toBe('accent-orange');
    expect(routeAccentClass(6)).toBe('accent-blue');
  });

  it('folds path prefixes beyond the third into a +N chip', () => {
    expect(visiblePathPrefixes(['/a', '/b'])).toEqual({ chips: ['/a', '/b'], more: 0 });
    expect(visiblePathPrefixes(['/a', '/b', '/c', '/d'])).toEqual({ chips: ['/a', '/b', '/c'], more: 1 });
  });

  it('derives warning and error status from the failure rate', () => {
    const route = { id: 'r', name: 'r', targetUrl: 'http://127.0.0.1:8080', enabled: true };
    expect(deriveRouteStatus(route, null)).toBe('running');
    expect(deriveRouteStatus({ ...route, enabled: false }, null)).toBe('stopped');
    expect(deriveRouteStatus(route, normalizeTrafficMetrics('r', { totalRequests: 100, failedRequests: 10 }))).toBe('warning');
    expect(deriveRouteStatus(route, normalizeTrafficMetrics('r', { totalRequests: 100, failedRequests: 40 }))).toBe('error');
    expect(deriveRouteStatus(route, normalizeTrafficMetrics('r', { totalRequests: 100, failedRequests: 1 }))).toBe('running');
  });

  it('filters routes by keyword across name, target, binding and prefixes', () => {
    const routes = [
      { id: 'a', name: '用户服务', targetUrl: 'http://127.0.0.1:8081', pathPrefixes: ['/api/user'], localIp: '127.0.0.1', localPort: 18081, enabled: true },
      { id: 'b', name: '订单服务', targetUrl: 'http://127.0.0.1:8082', pathPrefixes: ['/api/order'], localIp: '127.0.0.1', localPort: 18082, enabled: false },
    ];

    expect(filterRoutes(routes, { keyword: '用户', status: 'all' })).toHaveLength(1);
    expect(filterRoutes(routes, { keyword: '18082', status: 'all' })).toHaveLength(1);
    expect(filterRoutes(routes, { keyword: '/api/order', status: 'all' })).toHaveLength(1);
    expect(filterRoutes(routes, { keyword: '', status: 'enabled' })).toHaveLength(1);
    expect(filterRoutes(routes, { keyword: '', status: 'disabled' })[0].id).toBe('b');
  });

  it('sorts routes by traffic and latency using live metrics', () => {
    const routes = [
      { id: 'a', name: 'A', targetUrl: 'http://127.0.0.1:8081', enabled: true },
      { id: 'b', name: 'B', targetUrl: 'http://127.0.0.1:8082', enabled: true },
    ];
    const metrics = {
      a: normalizeTrafficMetrics('a', { requestsLastMinute: 10, averageDurationMs: 40 }),
      b: normalizeTrafficMetrics('b', { requestsLastMinute: 90, averageDurationMs: 5 }),
    };

    expect(sortRoutes(routes, 'traffic', metrics).map((route) => route.id)).toEqual(['b', 'a']);
    expect(sortRoutes(routes, 'latency', metrics).map((route) => route.id)).toEqual(['a', 'b']);
  });
});
