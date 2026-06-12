import { displayTargetUrl, effectivePathPrefixes, localBinding } from './route-utils';
import type { RouteConfig } from './types';

export type RouteStatusTone = 'enabled' | 'disabled';
export type RouteMetricTone = 'mint' | 'blue' | 'yellow' | 'pink';

export interface RouteDetailMetric {
  label: string;
  value: string;
  tone: RouteMetricTone;
}

export function routeStatusLabel(route: Pick<RouteConfig, 'enabled'>): string {
  return route.enabled ? '运行中' : '已停用';
}

export function routeStatusTone(route: Pick<RouteConfig, 'enabled'>): RouteStatusTone {
  return route.enabled ? 'enabled' : 'disabled';
}

export function formatJsonContent(content: string): string {
  try {
    return JSON.stringify(JSON.parse(content), null, 2);
  } catch (error) {
    return content;
  }
}

export function routeDetailMetrics(route: RouteConfig): RouteDetailMetric[] {
  const binding = localBinding(route.localIp, route.localPort);
  const prefixes = effectivePathPrefixes(route);
  return [
    { label: '监听地址', value: binding || '未配置', tone: 'mint' },
    { label: '默认目标', value: displayTargetUrl(route.targetUrl) || '未配置', tone: 'blue' },
    { label: '代理地址', value: displayTargetUrl(route.accessPageBaseUrl) || '未配置', tone: 'yellow' },
    { label: '路径数量', value: String(prefixes.length), tone: 'pink' },
  ];
}
