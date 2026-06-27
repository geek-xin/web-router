import { displayTargetUrl, effectivePathPrefixes, localBinding, routeAccessPath } from './route-utils';
import type { RouteConfig, RouteFormValues } from './types';

export type RouteStatusTone = 'enabled' | 'disabled';
export type RouteMetricTone = 'mint' | 'blue' | 'yellow' | 'pink';

export interface RouteDetailMetric {
  label: string;
  value: string;
  tone: RouteMetricTone;
}

export interface RouteTopologyModel {
  ingress: {
    value: string;
    note: string;
  };
  router: {
    summary: string;
    prefixChips: string[];
  };
  hit: {
    active: boolean;
    target: string;
    note: string;
  };
  miss: {
    target: string;
    note: string;
  };
  statusText: string;
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

export function buildRouteTopologyModel(values: RouteFormValues): RouteTopologyModel {
  const prefixes = values.pathPrefixes || [];
  const hasPrefixes = prefixes.length > 0;
  const accessPath = routeAccessPath({ accessPage: values.accessPage, pathPrefixes: prefixes });
  return {
    ingress: {
      value: localBinding(values.localIp, values.localPort) || '未配置监听端口',
      note: accessPath ? `访问 ${accessPath}` : '访问页未配置',
    },
    router: {
      summary: hasPrefixes ? `${prefixes.length} 个前缀` : '无前缀 · 全部兜底',
      prefixChips: compactPrefixChips(prefixes),
    },
    hit: {
      active: hasPrefixes,
      target: displayTargetUrl(values.accessPageBaseUrl) || '未配置代理地址',
      note: hasPrefixes ? '命中后转发到代理地址' : '未配置前缀，跳过代理分支',
    },
    miss: {
      target: displayTargetUrl(values.targetUrl) || '未配置兜底地址',
      note: '无前缀或未命中时转发',
    },
    statusText: values.enabled ? '启用：监听端口接收请求' : '停用：仅保留配置',
  };
}

function compactPrefixChips(prefixes: string[]): string[] {
  if (prefixes.length === 0) {
    return ['无前缀'];
  }
  if (prefixes.length <= 2) {
    return prefixes;
  }
  return [...prefixes.slice(0, 2), `+${prefixes.length - 2}`];
}
