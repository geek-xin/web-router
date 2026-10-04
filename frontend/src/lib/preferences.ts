/**
 * 本地 UI 偏好：主题（深/浅）与路由展示形式（卡片/列表）。
 *
 * 读写都接受注入的 Storage，便于测试；无 Storage（隐私模式、SSR）时静默降级。
 */

export type ThemeMode = 'dark' | 'light';
export type RouteViewMode = 'card' | 'list';
/** 侧边栏展开 / 收起。收起后只留图标，给内容区让出宽度。 */
export type SidebarMode = 'expanded' | 'collapsed';
/** 路由列表上方的概览带展开 / 收起。收起后压成一行摘要，把高度让给列表。 */
export type BandMode = 'expanded' | 'collapsed';

export const THEME_STORAGE_KEY = 'wrouter.theme';
export const ROUTE_VIEW_STORAGE_KEY = 'wrouter.routeView';
export const SIDEBAR_STORAGE_KEY = 'wrouter.sidebar';
export const BAND_STORAGE_KEY = 'wrouter.band';

/** 概览带默认展开；收起是用户在矮视口下主动腾出空间的手段。 */
export const DEFAULT_BAND_MODE: BandMode = 'expanded';

/**
 * 侧边栏默认状态：**收起**。
 * 首次访问（无存储偏好）时用窄轨启动，把宽度让给路由列表；
 * 用户手动展开后写入 localStorage，此后以存储值为准。
 */
export const DEFAULT_SIDEBAR_MODE: SidebarMode = 'collapsed';

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

export function normalizeSidebarMode(value?: string | null): SidebarMode | null {
  const text = (value || '').trim().toLowerCase();
  if (text === 'expanded' || text === 'collapsed') {
    return text;
  }
  return null;
}

export function normalizeBandMode(value?: string | null): BandMode | null {
  const text = (value || '').trim().toLowerCase();
  if (text === 'expanded' || text === 'collapsed') {
    return text;
  }
  return null;
}

/** 生效的概览带状态：用户显式选择优先，否则用默认值（展开）。 */
export function resolveBandMode(stored: BandMode | null): BandMode {
  return stored ?? DEFAULT_BAND_MODE;
}

/** 生效的侧边栏状态：用户显式选择优先，否则用默认值（收起）。 */
export function resolveSidebarMode(stored: SidebarMode | null): SidebarMode {
  return stored ?? DEFAULT_SIDEBAR_MODE;
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

export function nextSidebarMode(current: SidebarMode): SidebarMode {
  return current === 'expanded' ? 'collapsed' : 'expanded';
}

export function sidebarToggleLabel(mode: SidebarMode): string {
  return mode === 'expanded' ? '收起侧边栏' : '展开侧边栏';
}

export function nextBandMode(current: BandMode): BandMode {
  return current === 'expanded' ? 'collapsed' : 'expanded';
}

export function bandToggleLabel(mode: BandMode): string {
  return mode === 'expanded' ? '收起概览' : '展开概览';
}
