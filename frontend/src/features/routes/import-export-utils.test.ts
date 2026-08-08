import { describe, expect, it } from 'vitest';
import { parseRouteImportFile, routeExportFileName } from './import-export-utils';

describe('route import/export utils', () => {
  it('builds a stable export file name from the export timestamp', () => {
    expect(routeExportFileName('2026-07-26T14:03:22.000Z')).toBe('web-router-routes-20260726-140322.json');
  });

  it('parses a versioned route export payload for import', () => {
    const payload = parseRouteImportFile(JSON.stringify({
      version: 1,
      routes: [
        {
          name: 'orders',
          pathPrefixes: ['/orders'],
          targetUrl: 'http://127.0.0.1:8080',
          accessPageBaseUrl: 'http://127.0.0.1:8081',
          localIp: '127.0.0.1',
          localPort: 19091,
          enabled: true,
        },
      ],
    }));

    expect(payload.version).toBe(1);
    expect(payload.routes).toHaveLength(1);
    expect(payload.routes[0].name).toBe('orders');
  });

  it('accepts a bare route array and wraps it as version 1', () => {
    const payload = parseRouteImportFile(JSON.stringify([
      {
        name: 'orders',
        pathPrefixes: ['/orders'],
        targetUrl: 'http://127.0.0.1:8080',
        accessPageBaseUrl: 'http://127.0.0.1:8081',
        localIp: '127.0.0.1',
        localPort: 19091,
        enabled: true,
      },
    ]));

    expect(payload).toMatchObject({ version: 1, routes: [{ name: 'orders' }] });
  });

  it('rejects invalid import content with a clear message', () => {
    expect(() => parseRouteImportFile('{bad json')).toThrow('导入文件不是有效 JSON');
    expect(() => parseRouteImportFile(JSON.stringify({ version: 1 }))).toThrow('导入文件缺少 routes 数组');
  });
});
