import { describe, expect, it } from 'vitest';
import { activeLocalBinding, normalizePathPrefix, routeAccessUrl, validateRoutePayload } from './route-utils';

describe('route route-utils', () => {
  it('normalizes path prefixes and removes trailing slashes', () => {
    expect(normalizePathPrefix('/api//')).toBe('/api');
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
});
