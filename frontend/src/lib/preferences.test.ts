import { describe, expect, it, vi } from 'vitest';
import {
  applyTheme,
  nextRouteView,
  nextTheme,
  normalizeRouteView,
  normalizeTheme,
  prefersDarkScheme,
  readPreference,
  resolveTheme,
  routeViewLabel,
  safeStorage,
  themeLabel,
  THEME_STORAGE_KEY,
  writePreference,
} from './preferences';

function memoryStorage(initial: Record<string, string> = {}): Storage {
  const map = new Map(Object.entries(initial));
  return {
    get length() { return map.size; },
    clear: () => map.clear(),
    getItem: (key: string) => map.get(key) ?? null,
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => { map.delete(key); },
    setItem: (key: string, value: string) => { map.set(key, value); },
  } as Storage;
}

describe('preferences', () => {
  it('normalizes theme and view values', () => {
    expect(normalizeTheme('dark')).toBe('dark');
    expect(normalizeTheme('LIGHT')).toBe('light');
    expect(normalizeTheme(' system ')).toBeNull();
    expect(normalizeTheme(null)).toBeNull();
    expect(normalizeRouteView('list')).toBe('list');
    expect(normalizeRouteView('CARD')).toBe('card');
    expect(normalizeRouteView('table')).toBeNull();
  });

  it('follows the system when the user has not chosen a theme', () => {
    expect(resolveTheme(null, true)).toBe('dark');
    expect(resolveTheme(null, false)).toBe('light');
    expect(resolveTheme('light', true)).toBe('light');
    expect(resolveTheme('dark', false)).toBe('dark');
  });

  it('toggles theme and view mode', () => {
    expect(nextTheme('dark')).toBe('light');
    expect(nextTheme('light')).toBe('dark');
    expect(nextRouteView('card')).toBe('list');
    expect(nextRouteView('list')).toBe('card');
    expect(themeLabel('dark')).toBe('深色');
    expect(routeViewLabel('card')).toBe('卡片');
  });

  it('reads and writes preferences defensively', () => {
    const storage = memoryStorage();
    expect(readPreference(storage, THEME_STORAGE_KEY)).toBeNull();
    writePreference(storage, THEME_STORAGE_KEY, 'light');
    expect(readPreference(storage, THEME_STORAGE_KEY)).toBe('light');
    expect(readPreference(null, THEME_STORAGE_KEY)).toBeNull();
    writePreference(null, THEME_STORAGE_KEY, 'dark');
  });

  it('survives a storage that throws on access', () => {
    const hostile = {
      getItem: () => { throw new Error('blocked'); },
      setItem: () => { throw new Error('blocked'); },
    } as unknown as Storage;
    expect(readPreference(hostile, THEME_STORAGE_KEY)).toBeNull();
    expect(() => writePreference(hostile, THEME_STORAGE_KEY, 'dark')).not.toThrow();
  });

  it('applies the theme to a root element', () => {
    const root = { setAttribute: vi.fn(), style: {} } as unknown as HTMLElement;
    applyTheme('light', root);
    expect(root.setAttribute).toHaveBeenCalledWith('data-theme', 'light');
    expect((root.style as CSSStyleDeclaration).colorScheme).toBe('light');
    expect(() => applyTheme('dark', null)).not.toThrow();
  });

  it('defaults to dark when matchMedia is unavailable', () => {
    expect(prefersDarkScheme(null)).toBe(true);
    expect(prefersDarkScheme(() => ({ matches: false }) as MediaQueryList)).toBe(false);
  });

  it('never throws when resolving storage', () => {
    // jsdom 可能因安全策略让 localStorage 抛异常，安全取用必须降级为 null 而不是抛错
    expect(() => safeStorage()).not.toThrow();
    const storage = safeStorage();
    expect(storage === null || typeof storage === 'object').toBe(true);
  });
});
