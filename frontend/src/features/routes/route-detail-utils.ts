import type { RouteConfig, RouteFormValues } from './types';
import type { RouteTrafficMetrics } from './route-metrics';
import { effectivePathPrefixes, localBinding, displayTargetUrl, routeAccessPath, visiblePathPrefixes } from './route-utils';

export interface TopologyNodeModel {
  kicker: string;
  value: string;
  note: string;
}

/** 设计系统 §5.7：拓扑固定四段语义 Client → Gateway → Local Port → Target。 */
export interface RouteTopologyModel {
  client: TopologyNodeModel;
  gateway: TopologyNodeModel;
  localPort: TopologyNodeModel;
  target: TopologyNodeModel;
  fallback: TopologyNodeModel | null;
  prefixChips: string[];
  morePrefixes: number;
  active: boolean;
}

export function buildRouteTopologyModel(values: RouteFormValues): RouteTopologyModel {
  const prefixes = values.pathPrefixes || [];
  const hasPrefixes = prefixes.length > 0;
  const binding = localBinding(values.localIp, values.localPort);
  const accessPath = routeAccessPath({ accessPage: values.accessPage, pathPrefixes: prefixes });
  const { chips, more } = visiblePathPrefixes(prefixes);
  const target = displayTargetUrl(values.accessPageBaseUrl) || displayTargetUrl(values.targetUrl);

  return {
    client: {
      kicker: 'Client',
      value: accessPath || '未配置访问页',
      note: '浏览器 / curl / 调用方 · HTTP(S)',
    },
    gateway: {
      kicker: 'wrouter',
      value: hasPrefixes ? chips.join('  ') + (more > 0 ? '  +' + more : '') : '全部兜底',
      note: hasPrefixes ? '命中前缀 → 代理地址' : '未配置前缀 → 直接兜底',
    },
    localPort: {
      kicker: '本地端口',
      value: binding || '未配置监听端口',
      note: values.enabled ? '监听中' : '已停用，不监听',
    },
    target: {
      kicker: hasPrefixes && values.accessPageBaseUrl ? '目标服务（代理地址）' : '目标服务（默认地址）',
      value: target || '未配置目标地址',
      note: hasPrefixes && values.accessPageBaseUrl ? '命中路径前缀后转发到这里' : '默认转发地址',
    },
    fallback: hasPrefixes && values.accessPageBaseUrl
      ? {
          kicker: '未命中',
          value: displayTargetUrl(values.targetUrl) || '未配置默认地址',
          note: '未命中任何前缀时转发到默认地址',
        }
      : null,
    prefixChips: chips,
    morePrefixes: more,
    active: values.enabled === true,
  };
}

export interface RouteDetailMetric {
  label: string;
  value: string;
}

export function routeDetailMetrics(route: RouteConfig, metrics?: RouteTrafficMetrics | null): RouteDetailMetric[] {
  const prefixes = effectivePathPrefixes(route);
  return [
    { label: '请求数', value: metrics ? String(metrics.totalRequests) : '—' },
    { label: '成功率', value: metrics ? successRate(metrics) : '—' },
    { label: '平均延迟', value: metrics ? metrics.averageDurationMs + 'ms' : '—' },
    { label: '路径前缀', value: String(prefixes.length) },
    { label: '监听地址', value: localBinding(route.localIp, route.localPort) || '未配置' },
    { label: '默认地址', value: displayTargetUrl(route.targetUrl) || '未配置' },
  ];
}

function successRate(metrics: RouteTrafficMetrics): string {
  if (metrics.totalRequests <= 0) {
    return '—';
  }
  const rate = ((metrics.totalRequests - Math.min(metrics.totalRequests, metrics.failedRequests)) / metrics.totalRequests) * 100;
  return rate.toFixed(1) + '%';
}

export function formatJsonContent(content: string): string {
  try {
    return JSON.stringify(JSON.parse(content), null, 2);
  } catch (error) {
    return content;
  }
}

export function formatJsonText(value: string): string {
  return JSON.stringify(JSON.parse(value), null, 2);
}
