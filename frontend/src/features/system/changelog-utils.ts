import type { ReleaseHistory, ReleaseHistoryItem } from './types';
import { formatReleaseDate, formatReleaseNotes, releaseNotesPreview } from './version-utils';

/**
 * 顶栏「更新日志」铃铛的纯函数层：把 `/admin/api/update/history` 的返回
 * 归一化成面板可直接渲染的条目，并负责「未读」标记的读写。
 *
 * <p>为什么单独抽一层：未读判定（版本比较 + 存储回落）是最容易出错、
 * 也最值得单测的部分，组件里只留渲染与交互。</p>
 */

/** 「已读」标记的存储键：记录用户上次在铃铛面板里看过的版本号。 */
export const CHANGELOG_SEEN_STORAGE_KEY = 'wrouter.changelog.seen';

/** 面板默认展示的版本条数（最新一条完整展示，其余折叠）。 */
export const CHANGELOG_VISIBLE_LIMIT = 4;

/** 一条可直接渲染的更新日志。 */
export interface ChangelogEntry {
  version: string;
  /** 原始发布时间，用于排序/调试。 */
  publishedAt: string;
  /** 展示用日期（YYYY-MM-DD）。 */
  date: string;
  /** 是否为当前运行版本。 */
  current: boolean;
  /** 是否为预发布版本。 */
  prerelease: boolean;
  releaseUrl: string;
  /** 折叠态摘要：修订记录首行。 */
  summary: string;
  /** 展开态全文：去掉 Markdown 标记后的纯文本。 */
  notes: string;
}

/**
 * 取更新日志条目。
 *
 * <p>后端已按语义化版本倒序返回，这里不再重排，只做防御性过滤与截断：
 * 没有版本号的条目（异常数据）直接丢弃，避免面板出现空行。</p>
 */
export function changelogEntries(history: ReleaseHistory | null | undefined, limit = CHANGELOG_VISIBLE_LIMIT): ChangelogEntry[] {
  const releases = Array.isArray(history?.releases) ? history!.releases : [];
  return releases
    .filter((item): item is ReleaseHistoryItem => Boolean(item && text(item.version)))
    .slice(0, Math.max(1, limit))
    .map((item) => ({
      version: text(item.version),
      publishedAt: text(item.publishedAt),
      date: formatReleaseDate(item.publishedAt),
      current: item.current === true,
      prerelease: item.prerelease === true,
      releaseUrl: text(item.releaseUrl),
      summary: releaseNotesPreview(item.notes),
      notes: formatReleaseNotes(item.notes),
    }));
}

/** 面板头条：最新一个已发布版本（含预发布，预发布会带「预览」徽标）。 */
export function latestChangelogEntry(history: ReleaseHistory | null | undefined): ChangelogEntry | null {
  return changelogEntries(history, 1)[0] ?? null;
}

/**
 * 比较两个版本号：left 更新返回正数，相同返回 0，更旧返回负数。
 *
 * <p>只做面板需要的粗粒度判断（数字段 + 预发布后缀），不复刻后端
 * `ReleaseVersionComparator` 的全部规则——未读标记容错即可，
 * 真正的「有没有新版本」由 `/admin/api/update/check` 回答。</p>
 */
export function compareChangelogVersions(left: string, right: string): number {
  const a = parseVersion(left);
  const b = parseVersion(right);
  const length = Math.max(a.numbers.length, b.numbers.length);
  for (let index = 0; index < length; index += 1) {
    const diff = (a.numbers[index] ?? 0) - (b.numbers[index] ?? 0);
    if (diff !== 0) {
      return diff > 0 ? 1 : -1;
    }
  }
  // 数字段相同：正式版比预发布新（1.4.0 > 1.4.0-beta.1）
  if (a.prerelease === b.prerelease) {
    return 0;
  }
  if (!a.prerelease) {
    return 1;
  }
  if (!b.prerelease) {
    return -1;
  }
  return a.prerelease > b.prerelease ? 1 : -1;
}

/**
 * 是否有未读更新日志。
 *
 * <p>基线是「上次看过的版本」，没有记录时回落到**当前运行版本**——
 * 这样刚装好应用不会立刻亮红点（自己这一版的日志不算「新」），
 * 只有发布了更新的版本才会提示。降级安装（最新版本比已读版本旧）同样不提示。</p>
 */
export function hasUnreadChangelog(history: ReleaseHistory | null | undefined, seenVersion?: string | null): boolean {
  const latest = latestChangelogEntry(history);
  if (!latest) {
    return false;
  }
  const baseline = text(seenVersion) || text(history?.currentVersion);
  if (!baseline) {
    return false;
  }
  return compareChangelogVersions(latest.version, baseline) > 0;
}

/** 读取已读标记；无存储（隐私模式/测试）时返回空串。 */
export function readChangelogSeen(storage: Storage | null): string {
  if (!storage) {
    return '';
  }
  try {
    return text(storage.getItem(CHANGELOG_SEEN_STORAGE_KEY));
  } catch {
    return '';
  }
}

/** 写入已读标记；存储不可写时静默忽略（本次会话内仍以内存状态为准）。 */
export function markChangelogSeen(storage: Storage | null, version: string): void {
  const value = text(version);
  if (!storage || !value) {
    return;
  }
  try {
    storage.setItem(CHANGELOG_SEEN_STORAGE_KEY, value);
  } catch {
    // 配额或隐私模式：忽略，下次打开仍会提示未读
  }
}

/** 面板副标题：条数与仓库来源；读取失败时如实给出原因。 */
export function changelogSummary(history: ReleaseHistory | null, loading: boolean): string {
  if (loading && !history) {
    return '正在读取更新日志…';
  }
  if (!history) {
    return '更新日志暂不可用';
  }
  if (history.releases.length === 0) {
    return history.message || '暂无可用的发布版本';
  }
  const suffix = history.repository ? ' · ' + history.repository : '';
  const summary = '共 ' + history.releases.length + ' 个已发布版本' + suffix;
  // 有数据但仍带 message：后端刷新失败回落到缓存，必须如实标注
  return history.message ? summary + ' · ' + history.message : summary;
}

function parseVersion(value: string): { numbers: number[]; prerelease: string } {
  const cleaned = text(value).replace(/^v/i, '');
  const [core, ...rest] = cleaned.split('-');
  const numbers = core
    .split('.')
    .map((part) => Number.parseInt(part.replace(/\D/g, ''), 10))
    .map((part) => (Number.isFinite(part) ? part : 0));
  return { numbers, prerelease: rest.join('-') };
}

function text(value?: string | null): string {
  return (value || '').trim();
}
