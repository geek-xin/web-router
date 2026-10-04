import type { RouteConfig, RouteFormValues, RouteSortKey, RouteStatus, RouteStatusFilter, RouteValidationResult } from './types';
import type { RouteTrafficMetrics } from './route-metrics';
import { failureRatePercent } from './route-metrics';
import { normalizedOptionalText } from '@/lib/utils';

/** 设计系统 §2.2：按路由在列表中的稳定序号分配 Accent。 */
export const ROUTE_ACCENT_CLASSES = ['accent-blue', 'accent-cyan', 'accent-purple', 'accent-orange', 'accent-green', 'accent-slate'] as const;

export const ROUTE_PREFIX_CHIP_LIMIT = 3;

export function routeAccentClass(index: number): string {
  const normalized = Number.isFinite(index) ? Math.abs(Math.trunc(index)) : 0;
  return ROUTE_ACCENT_CLASSES[normalized % ROUTE_ACCENT_CLASSES.length];
}

export function effectivePathPrefixes(route: Pick<RouteConfig, 'pathPrefixes' | 'pathPrefix'>): string[] {
  const prefixes = route.pathPrefixes && route.pathPrefixes.length > 0 ? route.pathPrefixes : route.pathPrefix ? [route.pathPrefix] : [];
  return uniquePathPrefixes(prefixes.map(normalizePathPrefix).filter(Boolean));
}

/** 卡片上最多展示 3 个路径标签，其余折叠为 +N。 */
export function visiblePathPrefixes(prefixes: string[]): { chips: string[]; more: number } {
  if (prefixes.length <= ROUTE_PREFIX_CHIP_LIMIT) {
    return { chips: prefixes, more: 0 };
  }
  return { chips: prefixes.slice(0, ROUTE_PREFIX_CHIP_LIMIT), more: prefixes.length - ROUTE_PREFIX_CHIP_LIMIT };
}

export function normalizePathPrefix(value: string): string {
  let text = (value || '').trim();
  if (!text) {
    return '';
  }
  text = text.replace(/\/+/g, '/');
  if (!text.startsWith('/')) {
    text = '/' + text;
  }
  while (text.length > 1 && text.endsWith('/')) {
    text = text.slice(0, -1);
  }
  return text || '/';
}

export function uniquePathPrefixes(values: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    const normalized = normalizePathPrefix(value);
    if (normalized && !seen.has(normalized)) {
      seen.add(normalized);
      result.push(normalized);
    }
  }
  return result;
}

export function displayTargetUrl(targetUrl?: string | null): string {
  return (targetUrl || '').replace(/^https?:\/\//, '');
}

export function normalizedLocalIp(localIp?: string | null): string {
  return (localIp || '').trim() || '127.0.0.1';
}

export function localBinding(localIp?: string | null, localPort?: number | string | null): string {
  const port = (localPort || '').toString().trim();
  return port ? normalizedLocalIp(localIp) + ':' + port : '';
}

export function activeLocalBinding(route: RouteConfig): string {
  return route.enabled && route.localPort ? localBinding(route.localIp, route.localPort) : '';
}

export function hasLocalPort(route: RouteConfig): boolean {
  return route.localPort !== null && route.localPort !== undefined;
}

/**
 * 状态语义见设计系统实现映射 §4.3。
 * 优先使用最近 1 分钟的失败率（能反映当前健康状况），
 * 窗口内没有请求时退回累计失败率。
 */
export function deriveRouteStatus(route: RouteConfig, metrics?: RouteTrafficMetrics | null): RouteStatus {
  if (!route.enabled) {
    return 'stopped';
  }
  if (!metrics || metrics.totalRequests <= 0) {
    return 'running';
  }
  const failureRate = metrics.requestsLastMinute > 0
    ? failureRatePercent(metrics.requestsLastMinute, metrics.failedLastMinute)
    : failureRatePercent(metrics.totalRequests, metrics.failedRequests);
  if (failureRate >= 20) {
    return 'error';
  }
  if (failureRate > 5) {
    return 'warning';
  }
  return 'running';
}

export function routeStatusText(status: RouteStatus): string {
  switch (status) {
    case 'running':
      return '运行中';
    case 'stopped':
      return '已停用';
    case 'warning':
      return '异常';
    default:
      return '错误';
  }
}

/**
 * 状态点的颜色类，必须与 styles.css 里的 `.state-*` 一致。
 * 注意：`.status-*` 是日志表里 HTTP 状态码徽标的命名空间，两者不要混用。
 */
export function routeStatusDotClass(status: RouteStatus): string {
  switch (status) {
    case 'running':
      return 'state-running';
    case 'warning':
      return 'state-warning';
    case 'error':
      return 'state-error';
    default:
      return 'state-stopped';
  }
}

/** 卡片副标题：直接说明这条路由的转发语义，而不是装饰性文案。 */
export function routeBehaviorSummary(route: RouteConfig): string {
  if (!route.enabled) {
    return '已停用 · 仅保留配置，不注册 Gateway 路由';
  }
  const prefixes = effectivePathPrefixes(route);
  if (prefixes.length === 0) {
    return '无路径前缀 · 全部请求走默认地址';
  }
  if (route.accessPageBaseUrl) {
    return '命中前缀走代理地址，其余走默认地址';
  }
  return '仅配置了默认地址，命中前缀后仍转发到默认地址';
}

export interface RouteFilterInput {
  keyword: string;
  status: RouteStatusFilter;
}

export function filterRoutes(routes: RouteConfig[], filter: RouteFilterInput): RouteConfig[] {
  const keyword = filter.keyword.trim().toLowerCase();
  return routes
    .filter((route) => {
      if (filter.status === 'enabled') {
        return route.enabled;
      }
      if (filter.status === 'disabled') {
        return !route.enabled;
      }
      return true;
    })
    .filter((route) => {
      if (!keyword) {
        return true;
      }
      const haystack = [
        route.name,
        displayTargetUrl(route.targetUrl),
        displayTargetUrl(route.accessPageBaseUrl),
        localBinding(route.localIp, route.localPort),
        route.accessPage || '',
        route.id,
        ...effectivePathPrefixes(route),
      ];
      return haystack.some((value) => value.toLowerCase().includes(keyword));
    });
}

export function sortRoutes(routes: RouteConfig[], key: RouteSortKey, metricsById: Record<string, RouteTrafficMetrics | undefined> = {}): RouteConfig[] {
  const sorted = [...routes];
  switch (key) {
    case 'name':
      return sorted.sort((left, right) => left.name.localeCompare(right.name, 'zh-Hans-CN'));
    case 'traffic':
      return sorted.sort((left, right) => trafficOf(right, metricsById) - trafficOf(left, metricsById));
    case 'latency':
      return sorted.sort((left, right) => latencyOf(right, metricsById) - latencyOf(left, metricsById));
    default:
      return sorted;
  }
}

function trafficOf(route: RouteConfig, metricsById: Record<string, RouteTrafficMetrics | undefined>): number {
  return metricsById[route.id]?.requestsLastMinute ?? 0;
}

function latencyOf(route: RouteConfig, metricsById: Record<string, RouteTrafficMetrics | undefined>): number {
  return metricsById[route.id]?.averageDurationMs ?? 0;
}

export function isValidTargetUrl(targetUrl: string): boolean {
  return /^(https?:\/\/)?[-a-zA-Z0-9.]+:\d{1,5}$/.test((targetUrl || '').trim());
}

export function isValidLocalIp(localIp: string): boolean {
  const value = normalizedLocalIp(localIp);
  if (value === 'localhost') {
    return true;
  }
  const parts = value.split('.');
  if (parts.length !== 4) {
    return false;
  }
  return parts.every((part) => {
    if (!/^\d{1,3}$/.test(part)) {
      return false;
    }
    const number = Number(part);
    return number >= 0 && number <= 255;
  });
}

export function isValidLocalPort(localPort: string): boolean {
  const text = (localPort || '').trim();
  if (!/^\d{1,5}$/.test(text)) {
    return false;
  }
  const value = Number(text);
  return Number.isInteger(value) && value >= 1 && value <= 65535;
}

export interface RouteAccessInput {
  localBinding?: string | null;
  accessPage?: string | null;
  pathPrefixes?: string[] | null;
}

export function routeAccessPath(input: RouteAccessInput): string {
  const configured = (input.accessPage || '').trim();
  if (configured) {
    return configured;
  }
  return uniquePathPrefixes(input.pathPrefixes || [])[0] || '';
}

export function routeAccessUrl(input: RouteAccessInput): string {
  const path = routeAccessPath(input);
  if (!path) {
    return '';
  }
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  const binding = (input.localBinding || '').trim();
  if (!binding) {
    return '';
  }
  return 'http://' + binding + (path.startsWith('/') ? path : '/' + path);
}

export function validateRoutePayload(values: RouteFormValues, existingNames: string[] = [], existingBindings: string[] = []): RouteValidationResult {
  const errors: string[] = [];
  const name = values.name.trim();
  const targetUrl = values.targetUrl.trim();
  const accessPageBaseUrl = (values.accessPageBaseUrl || '').trim();
  const localIp = normalizedLocalIp(values.localIp);
  const localPortText = (values.localPort || '').trim();
  const pathPrefixes = uniquePathPrefixes(values.pathPrefixes || []);

  if (!name) {
    errors.push('请输入路由名称');
  }
  if (name.length > 50) {
    errors.push('路由名称不能超过 50 个字');
  }
  if (existingNames.includes(name)) {
    errors.push('路由名称已存在，不能重复');
  }
  if (!targetUrl || !isValidTargetUrl(targetUrl)) {
    errors.push('默认地址（兜底）格式不正确，如 192.168.1.100:8080 或 api.example.com:8080');
  }
  if (!isValidLocalIp(localIp)) {
    errors.push('监听 IP 格式不正确，如 127.0.0.1 或 localhost');
  }
  if (!isValidLocalPort(localPortText)) {
    errors.push('监听端口需为 1-65535 的整数');
  }
  for (const prefix of pathPrefixes) {
    if (!/^\/[a-zA-Z0-9_\-/]*$/.test(prefix)) {
      errors.push('路径前缀只允许英文、数字、下划线、连字符和 /');
      break;
    }
  }
  if (pathPrefixes.length > 0 && !accessPageBaseUrl) {
    errors.push('配置路径前缀时请输入代理地址');
  }
  if (accessPageBaseUrl && !isValidTargetUrl(accessPageBaseUrl)) {
    errors.push('代理地址格式不正确，如 192.168.1.100:8080 或 proxy.example.com:8080');
  }
  const binding = localBinding(localIp, localPortText);
  if (values.enabled === true && binding && existingBindings.includes(binding)) {
    errors.push('监听地址已被其他启用路由使用');
  }

  if (errors.length > 0) {
    return { errors };
  }

  return {
    errors,
    payload: {
      name,
      pathPrefix: pathPrefixes[0] || null,
      pathPrefixes,
      targetUrl,
      accessPageBaseUrl: normalizedOptionalText(accessPageBaseUrl),
      accessPage: normalizedOptionalText(values.accessPage),
      localIp,
      localPort: Number(localPortText),
      enabled: values.enabled === true,
    },
  };
}

export function nextCopyName(baseName: string, existingNames: string[]): string {
  const base = (baseName || '复制路由').trim();
  let candidate = base + '-copy';
  let index = 2;
  while (existingNames.includes(candidate)) {
    candidate = base + '-copy-' + index;
    index += 1;
  }
  return candidate;
}

export function routeToFormValues(route?: RouteConfig | null): RouteFormValues {
  return {
    name: route?.name || '',
    targetUrl: displayTargetUrl(route?.targetUrl),
    accessPageBaseUrl: displayTargetUrl(route?.accessPageBaseUrl),
    accessPage: route?.accessPage || '',
    localIp: normalizedLocalIp(route?.localIp),
    localPort: route?.localPort ? String(route.localPort) : '',
    pathPrefixes: effectivePathPrefixes(route || { pathPrefixes: [] }),
    enabled: route?.enabled === true,
  };
}
