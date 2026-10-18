import * as React from 'react';
import { Bell, ChevronDown, History, RefreshCw, Sparkles } from 'lucide-react';
import { fetchJson } from '@/lib/api';
import { safeStorage } from '@/lib/preferences';
import { cn } from '@/lib/utils';
import type { ReleaseHistory } from './types';
import { normalizeReleaseHistory } from './version-utils';
import {
  CHANGELOG_VISIBLE_LIMIT,
  changelogEntries,
  changelogSummary,
  hasUnreadChangelog,
  markChangelogSeen,
  readChangelogSeen,
  type ChangelogEntry,
} from './changelog-utils';

interface ChangelogBellProps {
  /** 兜底版本号：后端不可用时仍能判断「有没有新版本」。 */
  versionFallback: string;
  /** 打开完整的「版本与更新」抽屉。 */
  onOpenVersion: () => void;
}

/**
 * 顶栏「更新日志」铃铛。
 *
 * <p>回答一个问题：**最新一版改了什么？** 数据来自 `GET /admin/api/update/history`
 * （全部已发布版本，含预发布），不触发下载、不参与自动更新决策——
 * 那是「版本与更新」抽屉的职责。这里只做只读展示，因此可以从顶栏随处点开。</p>
 *
 * <p>未读点：最新版本比「上次看过的版本」新才亮。没有记录时以当前运行版本为基线，
 * 刚装好应用不会立刻亮红点。</p>
 */
export function ChangelogBell({ versionFallback, onOpenVersion }: ChangelogBellProps) {
  const [open, setOpen] = React.useState(false);
  const [history, setHistory] = React.useState<ReleaseHistory | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState('');
  const [seen, setSeen] = React.useState(() => readChangelogSeen(safeStorage()));
  /** 展开查看完整修订记录的版本号；同时只展开一条。 */
  const [expandedVersion, setExpandedVersion] = React.useState('');
  const rootRef = React.useRef<HTMLDivElement>(null);
  const triggerRef = React.useRef<HTMLButtonElement>(null);

  const load = React.useCallback(async () => {
    setLoading(true);
    try {
      const raw = await fetchJson<Partial<ReleaseHistory>>('/admin/api/update/history');
      setHistory(normalizeReleaseHistory(raw, versionFallback));
      setError('');
    } catch (loadError) {
      // 旧后端没有该接口或网络不可用：保留已有数据，只把原因写进面板
      setHistory((current) => current ?? normalizeReleaseHistory(null, versionFallback));
      setError(loadError instanceof Error ? loadError.message : '更新日志读取失败');
    } finally {
      setLoading(false);
    }
  }, [versionFallback]);

  // 挂载即静默拉一次：未读点必须在用户点开之前就有结论
  React.useEffect(() => {
    void load();
  }, [load]);

  // 打开面板：展开最新一条，并把「已读」推进到最新版本
  React.useEffect(() => {
    if (!open) {
      return;
    }
    const entries = changelogEntries(history, CHANGELOG_VISIBLE_LIMIT);
    setExpandedVersion((current) => current || entries[0]?.version || '');
    const latest = entries[0]?.version;
    if (latest && latest !== seen) {
      markChangelogSeen(safeStorage(), latest);
      setSeen(latest);
    }
  }, [open, history, seen]);

  // 点击面板外部或按 Esc 关闭；焦点回到铃铛按钮
  React.useEffect(() => {
    if (!open) {
      return;
    }
    function handlePointerDown(event: MouseEvent | TouchEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    }
    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open]);

  const entries = changelogEntries(history, CHANGELOG_VISIBLE_LIMIT);
  const unread = hasUnreadChangelog(history, seen);
  const latest = entries[0] ?? null;

  return (
    <div className="changelog-bell" ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className={cn('icon-button', unread && 'icon-button-dot')}
        aria-label={unread ? '更新日志（有新版本）' : '更新日志'}
        aria-haspopup="dialog"
        aria-expanded={open}
        title={latest ? '最新版本 v' + latest.version : '更新日志'}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell className="h-4 w-4" aria-hidden="true" />
      </button>

      {open && (
        <div className="changelog-pop" role="dialog" aria-label="更新日志">
          <header className="changelog-pop-head">
            <div className="changelog-pop-heading">
              <span className="changelog-pop-title">
                <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />
                更新日志
              </span>
              <p className="changelog-pop-sub" aria-live="polite">{changelogSummary(history, loading)}</p>
            </div>
            <button
              type="button"
              className="icon-button icon-button-sm"
              onClick={() => void load()}
              disabled={loading}
              aria-label="刷新更新日志"
              title="刷新更新日志"
            >
              <RefreshCw className={cn('h-3.5 w-3.5', loading && 'animate-spin')} aria-hidden="true" />
            </button>
          </header>

          {error && <p className="changelog-pop-error" role="alert">{error}</p>}

          {entries.length === 0 ? (
            <div className="changelog-pop-empty">
              <History className="h-4 w-4" aria-hidden="true" />
              <span>{loading ? '正在读取更新日志…' : '暂无可展示的版本记录'}</span>
            </div>
          ) : (
            <ol className="changelog-list" aria-label="最新更新内容">
              {entries.map((entry) => (
                <ChangelogRow
                  key={entry.version}
                  entry={entry}
                  expanded={expandedVersion === entry.version}
                  onToggle={() => setExpandedVersion(expandedVersion === entry.version ? '' : entry.version)}
                />
              ))}
            </ol>
          )}

          <footer className="changelog-pop-foot">
            <button
              type="button"
              className="changelog-pop-link"
              onClick={() => {
                setOpen(false);
                onOpenVersion();
              }}
            >
              查看全部版本与更新
            </button>
          </footer>
        </div>
      )}
    </div>
  );
}

/** 单条更新日志：版本号 + 日期 + 徽标，点击展开完整修订记录。 */
function ChangelogRow({ entry, expanded, onToggle }: { entry: ChangelogEntry; expanded: boolean; onToggle: () => void }) {
  return (
    <li className={cn('changelog-item', entry.current && 'changelog-item-current')}>
      <button
        type="button"
        className="changelog-item-head"
        onClick={onToggle}
        aria-expanded={expanded}
        aria-label={(expanded ? '收起 ' : '展开 ') + 'v' + entry.version + ' 的更新内容'}
      >
        <span className="changelog-version font-mono">v{entry.version}</span>
        {entry.current && <span className="chip chip-accent">当前</span>}
        {entry.prerelease && <span className="chip chip-warning">预览</span>}
        <span className="changelog-date font-mono">{entry.date}</span>
        <ChevronDown
          className={cn('changelog-chevron h-3.5 w-3.5', expanded && 'changelog-chevron-open')}
          aria-hidden="true"
        />
      </button>

      {expanded ? (
        <>
          <pre className="changelog-notes scroll-area">{entry.notes}</pre>
          {entry.releaseUrl && (
            <a className="changelog-link" href={entry.releaseUrl} target="_blank" rel="noreferrer">
              在 GitHub 查看该版本
            </a>
          )}
        </>
      ) : (
        <p className="changelog-preview">{entry.summary}</p>
      )}
    </li>
  );
}
