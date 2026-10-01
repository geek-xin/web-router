/**
 * 本地 UI 偏好：主题（深/浅）与路由展示形式（卡片/列表）。
 *
 * 读写都接受注入的 Storage，便于测试；无 Storage（隐私模式、SSR）时静默降级。
 */

export type ThemeMode = 'dark' | 'light';
export type RouteViewMode = 'card' | 'list';

export const THEME_STORAGE_KEY = 'wrouter.theme';
export const ROUTE_VIEW_STORAGE_KEY = 'wrouter.routeView';

/** 安全读取 storage；不可用（服务端渲染、隐私模式、测试环境）时返回 null。 */
export function safeStorage(): Storage | null {
  try {
    if (typeof window === 'undefined') {
      return null;
    }
    const storage = window.localStorage;
    // 某些环境（Node 自带 jsdom、隐私模式）localStorage 存在但为 undefined 或不可用
    if (!storage || typeof storage.getItem !== 'function' || typeof storage.setItem !== 'function') {
      return null;
    }
    return storage;
  } catch {
    return null;
  }
}

export function readPreference(storage: Storage | null, key: string): string | null {
  if (!storage) {
    return null;
  }
  try {
    const value = storage.getItem(key);
    return value && value.trim() ? value.trim() : null;
  } catch {
    return null;
  }
}

export function writePreference(storage: Storage | null, key: string, value: string): void {
  if (!storage) {
    return;
  }
  try {
    storage.setItem(key, value);
  } catch {
    // 存储不可写（配额/隐私模式）时忽略：偏好只在当前会话生效
  }
}

export function normalizeTheme(value?: string | null): ThemeMode | null {
  const text = (value || '').trim().toLowerCase();
  if (text === 'dark' || text === 'light') {
    return text;
  }
  return null;
}

export function normalizeRouteView(value?: string | null): RouteViewMode | null {
  const text = (value || '').trim().toLowerCase();
  if (text === 'card' || text === 'list') {
    return text;
  }
  return null;
}

/**
 * 生效主题：用户显式选择优先，否则跟随系统。
 */
export function resolveTheme(stored: ThemeMode | null, prefersDark: boolean): ThemeMode {
  return stored ?? (prefersDark ? 'dark' : 'light');
}

export function nextTheme(current: ThemeMode): ThemeMode {
  return current === 'dark' ? 'light' : 'dark';
}

/** 把主题写到 <html data-theme>；同时同步 color-scheme，让原生控件跟随。 */
export function applyTheme(theme: ThemeMode, root: HTMLElement | null = typeof document === 'undefined' ? null : document.documentElement): void {
  if (!root) {
    return;
  }
  root.setAttribute('data-theme', theme);
  root.style.colorScheme = theme;
}

export function prefersDarkScheme(matchMediaFn?: ((query: string) => MediaQueryList) | null): boolean {
  const fn = matchMediaFn ?? (typeof window === 'undefined' ? null : window.matchMedia?.bind(window));
  if (!fn) {
    return true;
  }
  try {
    return fn('(prefers-color-scheme: dark)').matches;
  } catch {
    return true;
  }
}

export function nextRouteView(current: RouteViewMode): RouteViewMode {
  return current === 'card' ? 'list' : 'card';
}

export function themeLabel(theme: ThemeMode): string {
  return theme === 'dark' ? '深色' : '浅色';
}

export function routeViewLabel(view: RouteViewMode): string {
  return view === 'card' ? '卡片' : '列表';
}
