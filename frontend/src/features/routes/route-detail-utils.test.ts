import { describe, expect, it } from 'vitest';
import type { RouteConfig } from './types';
import { buildRouteTopologyModel, formatJsonContent, routeDetailMetrics, routeStatusLabel, routeStatusTone } from './route-detail-utils';

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

describe('route-detail-utils', () => {
  it('labels enabled and disabled routes in Chinese', () => {
    expect(routeStatusLabel(enabledRoute)).toBe('运行中');
    expect(routeStatusLabel({ ...enabledRoute, enabled: false })).toBe('已停用');
  });

  it('returns a stable tone for route status', () => {
    expect(routeStatusTone(enabledRoute)).toBe('enabled');
    expect(routeStatusTone({ ...enabledRoute, enabled: false })).toBe('disabled');
  });

  it('formats valid JSON and preserves invalid JSON for editing', () => {
    expect(formatJsonContent('{"name":"A","enabled":true}')).toBe('{\n  "name": "A",\n  "enabled": true\n}');
    expect(formatJsonContent('{bad json')).toBe('{bad json');
  });

  it('builds route detail metrics from normalized route fields', () => {
    expect(routeDetailMetrics(enabledRoute)).toEqual([
      { label: '监听地址', value: '127.0.0.1:9191', tone: 'mint' },
      { label: '默认目标', value: '127.0.0.1:8080', tone: 'blue' },
      { label: '代理地址', value: '127.0.0.1:9999', tone: 'yellow' },
      { label: '路径数量', value: '2', tone: 'pink' },
    ]);
  });

  it('summarizes route topology for the compact graphical flow', () => {
    const model = buildRouteTopologyModel({
      name: '演示路由',
      targetUrl: '127.0.0.1:8080',
      accessPageBaseUrl: '127.0.0.1:9999',
      accessPage: '',
      localIp: '127.0.0.1',
      localPort: '9191',
      pathPrefixes: ['/portal', '/api', '/assets'],
      enabled: true,
    });

    expect(model.ingress.value).toBe('127.0.0.1:9191');
    expect(model.ingress.note).toBe('访问 /portal');
    expect(model.router.summary).toBe('3 个前缀');
    expect(model.router.prefixChips).toEqual(['/portal', '/api', '+1']);
    expect(model.hit.active).toBe(true);
    expect(model.hit.target).toBe('127.0.0.1:9999');
    expect(model.miss.target).toBe('127.0.0.1:8080');
  });

  it('marks empty-prefix topology as fallback-only', () => {
    const model = buildRouteTopologyModel({
      name: '兜底路由',
      targetUrl: '127.0.0.1:8080',
      accessPageBaseUrl: '',
      accessPage: '',
      localIp: '127.0.0.1',
      localPort: '9191',
      pathPrefixes: [],
      enabled: false,
    });

    expect(model.router.summary).toBe('无前缀 · 全部兜底');
    expect(model.router.prefixChips).toEqual(['无前缀']);
    expect(model.hit.active).toBe(false);
    expect(model.hit.note).toBe('未配置前缀，跳过代理分支');
    expect(model.miss.note).toBe('无前缀或未命中时转发');
  });
});
