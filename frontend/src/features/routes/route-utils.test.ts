import { describe, expect, it } from 'vitest';
import { normalizePathPrefix, routeAccessUrl, validateRoutePayload } from './route-utils';

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
});
