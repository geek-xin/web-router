import { describe, expect, it } from 'vitest';
import type { RouteConfig } from './types';
import { formatJsonContent, routeDetailMetrics, routeStatusLabel, routeStatusTone } from './route-detail-utils';

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
});
