import type { RouteConfig, RouteFormValues, RouteValidationResult } from './types';
import { normalizedOptionalText } from '@/lib/utils';

const PREFIX_TONE_CLASSES = ['route-prefix-chip-blue', 'route-prefix-chip-mint', 'route-prefix-chip-yellow', 'route-prefix-chip-pink'] as const;

export function effectivePathPrefixes(route: Pick<RouteConfig, 'pathPrefixes' | 'pathPrefix'>): string[] {
  const prefixes = route.pathPrefixes && route.pathPrefixes.length > 0 ? route.pathPrefixes : route.pathPrefix ? [route.pathPrefix] : [];
  return uniquePathPrefixes(prefixes.map(normalizePathPrefix).filter(Boolean));
}

export function prefixToneClass(index: number): string {
  return PREFIX_TONE_CLASSES[Math.abs(index) % PREFIX_TONE_CLASSES.length];
}

export function routeCardToneClass(_route: Pick<RouteConfig, 'enabled'>, _index: number): string {
  return 'route-card-tone-pink';
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
