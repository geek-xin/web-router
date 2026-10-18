import { describe, expect, it } from 'vitest';
import {
  CHANGELOG_SEEN_STORAGE_KEY,
  CHANGELOG_VISIBLE_LIMIT,
  changelogEntries,
  changelogSummary,
  compareChangelogVersions,
  hasUnreadChangelog,
  latestChangelogEntry,
  markChangelogSeen,
  readChangelogSeen,
} from './changelog-utils';
import type { ReleaseHistory, ReleaseHistoryItem } from './types';

function release(overrides: Partial<ReleaseHistoryItem> = {}): ReleaseHistoryItem {
  return {
    version: '1.3.0',
    name: 'wrouter v1.3.0',
    publishedAt: '2026-10-01T13:31:41Z',
    releaseUrl: 'https://github.com/geek-xin/web-router/releases/tag/v1.3.0',
    prerelease: false,
    current: true,
    notes: '## wrouter 1.3.0\n\n- 多平台打包\n- 自动更新',
    ...overrides,
  };
}

function history(releases: ReleaseHistoryItem[], overrides: Partial<ReleaseHistory> = {}): ReleaseHistory {
  return {
    currentVersion: '1.3.0',
    repository: 'geek-xin/web-router',
    releases,
    message: '',
    ...overrides,
  };
}

function memoryStorage(seed: Record<string, string> = {}): Storage {
  const map = new Map(Object.entries(seed));
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => map.get(key) ?? null,
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => void map.delete(key),
    setItem: (key: string, value: string) => void map.set(key, value),
  } as Storage;
}

describe('changelogEntries', () => {
  it('把历史版本归一化成可渲染条目，折叠态用首行摘要', () => {
    const entries = changelogEntries(history([release()]));
    expect(entries).toHaveLength(1);
    expect(entries[0]).toMatchObject({
      version: '1.3.0',
      date: '2026-10-01',
      current: true,
      prerelease: false,
      summary: 'wrouter 1.3.0',
    });
    // 展开态是去掉 Markdown 标记的纯文本，保留内容
    expect(entries[0].notes).toContain('多平台打包');
    expect(entries[0].notes).not.toContain('##');
  });

  it('丢弃没有版本号的异常条目，并按 limit 截断', () => {
    const items = [
      release({ version: '1.3.0' }),
      release({ version: '' }),
      release({ version: '1.2.0' }),
      release({ version: '1.1.0' }),
      release({ version: '1.0.0' }),
    ];
    const entries = changelogEntries(history(items), 2);
    expect(entries.map((entry) => entry.version)).toEqual(['1.3.0', '1.2.0']);
  });

  it('空历史与缺字段时返回空数组，不抛错', () => {
    expect(changelogEntries(null)).toEqual([]);
    expect(changelogEntries(history([]))).toEqual([]);
    expect(latestChangelogEntry(undefined)).toBeNull();
    expect(CHANGELOG_VISIBLE_LIMIT).toBeGreaterThan(0);
  });
});

describe('compareChangelogVersions', () => {
  it('按数字段比较，并让正式版高于同号预发布', () => {
    expect(compareChangelogVersions('1.4.0', '1.3.0')).toBeGreaterThan(0);
    expect(compareChangelogVersions('1.3.0', '1.3.0')).toBe(0);
    expect(compareChangelogVersions('1.2.0', '1.3.0')).toBeLessThan(0);
    expect(compareChangelogVersions('v1.3.0', '1.3.0')).toBe(0);
    expect(compareChangelogVersions('1.4.0', '1.4.0-beta.1')).toBeGreaterThan(0);
    expect(compareChangelogVersions('1.4.0-beta.2', '1.4.0-beta.1')).toBeGreaterThan(0);
    // 位数不同时缺失段按 0 处理：1.4 与 1.4.0 视为同一版本
    expect(compareChangelogVersions('1.4', '1.4.0')).toBe(0);
  });
});

describe('hasUnreadChangelog', () => {
  it('最新版本比已读版本新才算未读', () => {
    const data = history([release({ version: '1.4.0', current: false }), release()]);
    expect(hasUnreadChangelog(data, '1.3.0')).toBe(true);
    expect(hasUnreadChangelog(data, '1.4.0')).toBe(false);
    // 已读版本比最新还新（降级安装）时不提示
    expect(hasUnreadChangelog(data, '1.5.0')).toBe(false);
  });

  it('没有已读记录时以当前运行版本为基线，刚装好不亮红点', () => {
    const same = history([release({ version: '1.3.0' })]);
    expect(hasUnreadChangelog(same, '')).toBe(false);
    const newer = history([release({ version: '1.4.0', current: false }), release()]);
    expect(hasUnreadChangelog(newer, '')).toBe(true);
  });

  it('没有数据时不提示未读', () => {
    expect(hasUnreadChangelog(null, '1.0.0')).toBe(false);
    expect(hasUnreadChangelog(history([]), '1.0.0')).toBe(false);
  });
});

describe('已读标记读写', () => {
  it('写入后能读回，存储不可用时静默降级', () => {
    const storage = memoryStorage();
    expect(readChangelogSeen(storage)).toBe('');
    markChangelogSeen(storage, '1.4.0');
    expect(readChangelogSeen(storage)).toBe('1.4.0');
    expect(storage.getItem(CHANGELOG_SEEN_STORAGE_KEY)).toBe('1.4.0');

    expect(readChangelogSeen(null)).toBe('');
    expect(() => markChangelogSeen(null, '1.4.0')).not.toThrow();
    expect(() => markChangelogSeen(storage, '')).not.toThrow();
  });

  it('存储抛错时不影响渲染', () => {
    const broken = {
      getItem: () => {
        throw new Error('denied');
      },
      setItem: () => {
        throw new Error('denied');
      },
    } as unknown as Storage;
    expect(readChangelogSeen(broken)).toBe('');
    expect(() => markChangelogSeen(broken, '1.4.0')).not.toThrow();
  });
});

describe('changelogSummary', () => {
  it('加载中、无数据、有数据与缓存回落各有明确文案', () => {
    expect(changelogSummary(null, true)).toBe('正在读取更新日志…');
    expect(changelogSummary(null, false)).toBe('更新日志暂不可用');
    expect(changelogSummary(history([], { message: '读取历史版本失败' }), false)).toBe('读取历史版本失败');
    expect(changelogSummary(history([release()]), false)).toBe('共 1 个已发布版本 · geek-xin/web-router');
    // 有数据但仍带 message：缓存回落必须如实标注
    expect(changelogSummary(history([release()], { message: '以下为缓存数据' }), false)).toContain('以下为缓存数据');
  });
});
