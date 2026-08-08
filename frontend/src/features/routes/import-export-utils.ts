import type { RouteConfigPayload } from './types';

export interface RouteImportPayload {
  version: number;
  routes: RouteConfigPayload[];
}

export function routeExportFileName(exportedAt: string): string {
  const date = new Date(exportedAt);
  const stamp = [
    date.getUTCFullYear(),
    pad(date.getUTCMonth() + 1),
    pad(date.getUTCDate()),
    '-',
    pad(date.getUTCHours()),
    pad(date.getUTCMinutes()),
    pad(date.getUTCSeconds()),
  ].join('');
  return `web-router-routes-${stamp}.json`;
}

export function parseRouteImportFile(content: string): RouteImportPayload {
  let value: unknown;
  try {
    value = JSON.parse(content);
  } catch (error) {
    throw new Error('导入文件不是有效 JSON');
  }

  if (Array.isArray(value)) {
    return { version: 1, routes: value as RouteConfigPayload[] };
  }

  if (!isRecord(value) || !Array.isArray(value.routes)) {
    throw new Error('导入文件缺少 routes 数组');
  }

  return {
    version: typeof value.version === 'number' ? value.version : 1,
    routes: value.routes as RouteConfigPayload[],
  };
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
