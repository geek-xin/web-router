import * as React from 'react';
import { Eye, Pause, Plus, RefreshCw, Search, X } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { Input, Select } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { fetchJson } from '@/lib/api';
import { copyText, formatDuration, formatTime } from '@/lib/utils';
import type { RouteConfig } from '@/features/routes/types';
import { formatCompactNumber, formatLatency, successRatePercent } from '@/features/routes/route-metrics';
import type { LogViewState, ProxyRequestLogEntry, ProxyRequestLogSnapshot } from './types';
import { addLogEntry, buildDurationTopLogs, buildPathDurationStats, buildPathMaxDurationStats, buildPathStats, normalizedLogPath, ROUTE_LOG_MAX_RECENT, snapshotToState } from './log-utils';

interface RouteLogDialogProps {
  open: boolean;
  route: RouteConfig | null;
  onOpenChange: (open: boolean) => void;
}

const initialState: LogViewState = {
  totalRequests: 0,
  failedRequests: 0,
  slowRequests: 0,
  totalDurationMs: 0,
  requestsByIp: {},
  pathStats: {},
  pathDurationStats: {},
  pathMaxDurationStats: {},
  durationTopLogs: [],
  recentLogs: [],
};

const refreshModes = [
  { label: '实时刷新', intervalMs: 0 },
  { label: '1秒刷新', intervalMs: 1000 },
  { label: '3秒刷新', intervalMs: 3000 },
  { label: '5秒刷新', intervalMs: 5000 },
  { label: '暂停刷新', intervalMs: -1 },
];

export function RouteLogDialog({ open, route, onOpenChange }: RouteLogDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(98vw,1180px)]">
        <RouteLogPanel open={open} route={route} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface RouteLogPanelProps {
  open: boolean;
  /** 传 null 表示「全部路由」：聚合所有路由的日志，无需先选一条。 */
  route: RouteConfig | null;
}

export function RouteLogPanel({ open, route }: RouteLogPanelProps) {
  const [state, setState] = React.useState<LogViewState>(initialState);
  const [activeTab, setActiveTab] = React.useState('realtime');
  const [modeIndex, setModeIndex] = React.useState(0);
  const [limit, setLimit] = React.useState(50);
  const [pathSearch, setPathSearch] = React.useState('');
  const [detail, setDetail] = React.useState<ProxyRequestLogEntry | null>(null);
  const [diagnostics, setDiagnostics] = React.useState<ProxyRequestLogEntry[]>([]);
  const [status, setStatus] = React.useState('等待日志');
  const sourceRef = React.useRef<EventSource | null>(null);
  const pollRef = React.useRef<number | null>(null);

  const routeId = route?.id;
  // 未选路由时走「全部路由」聚合接口，而不是什么都不做
  const scoped = Boolean(routeId);
  const snapshotUrl = scoped
    ? '/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId!)
    : '/admin/api/proxy-logs';
  const streamUrl = scoped
    ? '/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId!) + '/stream'
    : '/admin/api/proxy-logs/stream';

  const refreshSnapshot = React.useCallback(async (showError = false) => {
    try {
      const snapshot = await fetchJson<ProxyRequestLogSnapshot>(snapshotUrl);
      setState(snapshotToState(snapshot));
      setStatus(scoped ? '日志快照已更新' : '全部路由日志已更新');
    } catch (error) {
      if (showError) {
        toast.error(error instanceof Error ? error.message : '日志加载失败');
      }
      setStatus('日志刷新失败');
    }
  }, [snapshotUrl, scoped]);

  const stopStream = React.useCallback(() => {
    sourceRef.current?.close();
    sourceRef.current = null;
  }, []);

  const stopPolling = React.useCallback(() => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const connectStream = React.useCallback(() => {
    stopStream();
    // 全部路由模式不要求路由处于启用状态；单路由模式沿用「停用不连流」的既有语义
    const streamAllowed = scoped ? Boolean(route?.enabled) : true;
    if (!streamAllowed || !window.EventSource || refreshModes[modeIndex].intervalMs !== 0) {
      return;
    }
    const source = new EventSource(streamUrl);
    sourceRef.current = source;
    setStatus('实时刷新已连接');
    source.addEventListener('proxy-request', (event) => {
      const entry = JSON.parse((event as MessageEvent).data) as ProxyRequestLogEntry;
      setState((current) => addLogEntry(current, entry));
      setStatus('收到实时请求');
    });
    source.onerror = () => {
      setStatus('实时连接断开，使用快照刷新');
      stopStream();
      void refreshSnapshot(false);
    };
  }, [modeIndex, refreshSnapshot, route?.enabled, scoped, streamUrl, stopStream]);

  // 切换作用域（全部 ↔ 单路由）时重置视图并拉一次快照
  React.useEffect(() => {
    if (!open) {
      return;
    }
    setState(initialState);
    setDiagnostics([]);
    setDetail(null);
    setActiveTab('realtime');
    void refreshSnapshot(true);
  }, [open, routeId, refreshSnapshot]);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    stopPolling();
    stopStream();
    const mode = refreshModes[modeIndex];
    if (mode.intervalMs === 0) {
      connectStream();
    } else if (mode.intervalMs > 0) {
      setStatus(mode.label);
      pollRef.current = window.setInterval(() => void refreshSnapshot(false), mode.intervalMs);
    } else {
      setStatus('已暂停刷新');
    }
    return () => {
      stopPolling();
      stopStream();
    };
  }, [connectStream, modeIndex, open, refreshSnapshot, routeId, stopPolling, stopStream]);

  const recentLogs = filterLogs(state.recentLogs, pathSearch).slice(0, limit);
  const slowLogs = filterLogs(state.durationTopLogs.length > 0 ? state.durationTopLogs : buildDurationTopLogs(state.recentLogs), pathSearch).slice(0, limit);
  const pathRows = pathStatsRows(state, pathSearch).slice(0, limit);
  const successRate = successRatePercent(state.totalRequests, state.failedRequests);
  const averageDuration = state.totalRequests > 0 ? Math.round(state.totalDurationMs / state.totalRequests) : 0;

  function addDiagnostic(entry: ProxyRequestLogEntry) {
    const key = diagnosticKey(entry);
    setDiagnostics((current) => current.some((item) => diagnosticKey(item) === key) ? current : [entry, ...current].slice(0, 20));
    toast.success('已加入诊断分析');
  }

  return (
    <div className="grid gap-4">
      <div className="flex min-w-0 flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-start lg:justify-between">
        <div className="min-w-0">
          <h3 className="toolbar-title">路由日志 · {route?.name || '全部路由'}</h3>
          <p className="mt-1 font-mono text-[11px]" style={{ color: 'var(--surface-ink-subtle)' }}>{status}</p>
        </div>
        {/* pr-10 为对话框右上角关闭按钮预留位置；独立使用时不需要 */}
        <div className="flex min-w-0 flex-wrap items-center gap-2 lg:pr-10">
          <Button size="sm" variant="outline" onClick={() => setModeIndex((index) => (index + 1) % refreshModes.length)}>
            {refreshModes[modeIndex].intervalMs === -1 ? <Pause className="h-3.5 w-3.5" /> : <RefreshCw className="h-3.5 w-3.5" />}
            {refreshModes[modeIndex].label}
          </Button>
          <Button size="sm" variant="primary" onClick={() => void refreshSnapshot(true)}>立即刷新</Button>
        </div>
      </div>

      <div className="metric-grid metric-grid-5">
        <Metric label="请求数" value={formatCompactNumber(state.totalRequests)} />
        <Metric label="失败请求" value={formatCompactNumber(state.failedRequests)} />
        <Metric label="慢请求" value={formatCompactNumber(state.slowRequests)} />
        <Metric label="成功率" value={successRate} />
        <Metric label="平均延迟" value={formatLatency(averageDuration)} />
      </div>

      {/* min-w-0：面板里的宽表格（min-w-[940px]）不得把网格轨道撑宽，否则头部按钮会被挤出容器 */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="min-w-0">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <TabsList className="border-b-0">
            <TabsTrigger value="realtime">实时日志</TabsTrigger>
            <TabsTrigger value="top">总耗时 Top</TabsTrigger>
            <TabsTrigger value="slow">单耗时 Top</TabsTrigger>
            <TabsTrigger value="diagnostics">诊断分析</TabsTrigger>
          </TabsList>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <label className="search-field">
              <span className="sr-only">搜索路径</span>
              <Search className="search-field-icon" aria-hidden="true" />
              <Input value={pathSearch} onChange={(event) => setPathSearch(event.target.value)} type="search" placeholder="搜索路径" />
            </label>
            <label className="flex shrink-0 items-center gap-2 whitespace-nowrap text-[12px]" style={{ color: 'var(--surface-ink-muted)' }}>
              显示
              <Select className="w-[76px] min-w-[76px]" value={String(limit)} onChange={(event) => setLimit(normalizeLimit(event.target.value))} aria-label="显示条数">
                {[20, 50, 100].map((value) => <option key={value} value={value}>{value}</option>)}
              </Select>
              条
            </label>
          </div>
        </div>

        <TabsContent value="top">
          <PathStatsTable rows={pathRows} />
        </TabsContent>
        <TabsContent value="realtime">
          <LogTable logs={recentLogs} emptyText="暂无代理请求" onDetail={setDetail} onAnalyze={addDiagnostic} />
        </TabsContent>
        <TabsContent value="slow">
          <LogTable logs={slowLogs} emptyText="暂无代理请求" onDetail={setDetail} onAnalyze={addDiagnostic} durationLabel="单次耗时" />
        </TabsContent>
        <TabsContent value="diagnostics">
          {diagnostics.length === 0 ? (
            <div className="empty-state">
              <strong className="empty-title">尚未选择请求</strong>
              <span className="empty-copy">在「实时日志」或「单耗时 Top」中点击「拷贝分析」，把请求逐条加入这里形成诊断列表。</span>
            </div>
          ) : (
            <LogTable logs={diagnostics} emptyText="暂无诊断请求" onDetail={setDetail} onAnalyze={(entry) => setDiagnostics((current) => current.filter((item) => diagnosticKey(item) !== diagnosticKey(entry)))} analyzeLabel="移除" />
          )}
        </TabsContent>
      </Tabs>

      {detail && <LogDetailDrawer entry={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="field">
      <span className="route-field-label">{label}</span>
      <span className="metric-figure">{value}</span>
    </div>
  );
}

function PathStatsTable({ rows }: { rows: Array<{ path: string; count: number; total: number; max: number }> }) {
  return (
    <div className="table-wrap scroll-area">
      <table className="data-table min-w-[720px]">
        <thead>
          <tr>
            <th className="w-14">序号</th>
            <th>路径</th>
            <th className="w-24">请求数</th>
            <th className="w-24">总耗时</th>
            <th className="w-24">最长单次</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr><td colSpan={5} className="text-center" style={{ color: 'var(--surface-ink-subtle)' }}>暂无请求</td></tr>
          ) : rows.map((row, index) => (
            <tr key={row.path}>
              <td className="cell-mono">{index + 1}</td>
              <td className="cell-mono max-w-[420px] truncate" title={row.path}>{row.path}</td>
              <td className="cell-mono">{row.count}</td>
              <td className="cell-mono">{formatDuration(row.total)}</td>
              <td className="cell-mono">{formatDuration(row.max)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LogTable({ logs, emptyText, durationLabel = '耗时', analyzeLabel = '拷贝分析', onDetail, onAnalyze }: { logs: ProxyRequestLogEntry[]; emptyText: string; durationLabel?: string; analyzeLabel?: string; onDetail: (entry: ProxyRequestLogEntry) => void; onAnalyze: (entry: ProxyRequestLogEntry) => void }) {
  return (
    <div className="table-wrap scroll-area">
      <table className="data-table min-w-[940px]">
        <thead>
          <tr>
            <th className="w-14">序号</th>
            <th className="w-24">时间</th>
            <th className="w-20">方法</th>
            <th className="w-44">实际访问</th>
            <th>路径</th>
            <th className="w-20">状态</th>
            <th className="w-24">{durationLabel}</th>
            <th className="w-44">操作</th>
          </tr>
        </thead>
        <tbody>
          {logs.length === 0 ? (
            <tr><td colSpan={8} className="text-center" style={{ color: 'var(--surface-ink-subtle)' }}>{emptyText}</td></tr>
          ) : logs.map((entry, index) => (
            <tr key={diagnosticKey(entry) + index}>
              <td className="cell-mono">{index + 1}</td>
              <td className="cell-mono whitespace-nowrap">{formatTime(logTimestamp(entry))}</td>
              <td><span className={methodBadgeClass(entry.method)}>{entry.method || '-'}</span></td>
              <td className="cell-mono max-w-[190px] truncate" title={entry.accessAddress || '-'}>{entry.accessAddress || '-'}</td>
              <td className="cell-mono max-w-[320px] truncate" title={normalizedLogPath(entry.path)}>{normalizedLogPath(entry.path)}</td>
              <td><span className={statusBadgeClass(entry.status)}>{entry.status || '-'}</span></td>
              <td className="cell-mono">{formatDuration(entry.durationMs)}</td>
              <td>
                <div className="flex gap-1.5">
                  <Button size="sm" variant="ghost" onClick={() => onDetail(entry)}><Eye className="h-3.5 w-3.5" />详情</Button>
                  <Button size="sm" variant="ghost" onClick={() => onAnalyze(entry)}>{analyzeLabel === '拷贝分析' ? <Plus className="h-3.5 w-3.5" /> : null}{analyzeLabel}</Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function LogDetailDrawer({ entry, onClose }: { entry: ProxyRequestLogEntry; onClose: () => void }) {
  const closeButtonRef = React.useRef<HTMLButtonElement | null>(null);

  React.useEffect(() => {
    closeButtonRef.current?.focus({ preventScroll: true });
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <>
      <button className="drawer-scrim" type="button" aria-label="关闭请求详情" onClick={onClose} />
      <aside className="drawer scroll-area" role="dialog" aria-modal="true" aria-labelledby="route-log-detail-title">
        <header className="drawer-head">
          <div className="drawer-title-row">
            <div className="min-w-0 flex-1">
              <h3 id="route-log-detail-title" className="drawer-title">请求详情</h3>
            </div>
            <Button ref={closeButtonRef} size="sm" variant="outline" onClick={onClose}><X className="h-3.5 w-3.5" />关闭</Button>
          </div>
        </header>
        <div className="drawer-body scroll-area">
          <div className="field-grid">
            <Detail label="时间" value={formatTime(logTimestamp(entry))} />
            <Detail label="方法" value={entry.method || '-'} />
            <Detail label="状态" value={String(entry.status || '-')} />
            <Detail label="耗时" value={formatDuration(entry.durationMs)} />
            <Detail label="实际访问" value={entry.accessAddress || '-'} />
            <Detail label="客户端 IP" value={entry.clientIp || '-'} />
          </div>
          <div className="mt-4 grid gap-3">
            <Pre label="路径" value={normalizedLogPath(entry.path)} />
            <Pre label="请求参数" value={pretty(entry.requestParams)} />
            <Pre label="请求体" value={pretty(entry.requestBody)} />
            <Pre label="返回预览" value={pretty(entry.responseBody)} />
          </div>
        </div>
        <footer className="drawer-actions">
          <Button variant="outline" onClick={() => void copyText(diagnosticContextText([entry]))}>拷贝诊断上下文</Button>
        </footer>
      </aside>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="field">
      <span className="route-field-label">{label}</span>
      <span className="field-value" title={value}>{value}</span>
    </div>
  );
}

function Pre({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <h4 className="route-field-label mb-1">{label}</h4>
      <pre
        className="scroll-area max-h-40 overflow-auto p-3 font-mono text-[11.5px] leading-5"
        style={{ border: '1px solid var(--surface-line)', borderRadius: 'var(--radius-card)', background: 'var(--surface-sunken)', color: 'var(--surface-ink-muted)', margin: 0 }}
      >{value}</pre>
    </div>
  );
}

function filterLogs(logs: ProxyRequestLogEntry[], keyword: string): ProxyRequestLogEntry[] {
  const text = keyword.trim().toLowerCase();
  if (!text) {
    return logs;
  }
  return logs.filter((entry) => normalizedLogPath(entry.path).toLowerCase().includes(text));
}

function pathStatsRows(state: LogViewState, keyword: string) {
  const pathDurationStats = Object.keys(state.pathDurationStats).length > 0 ? state.pathDurationStats : buildPathDurationStats(state.recentLogs);
  const pathStats = Object.keys(state.pathStats).length > 0 ? state.pathStats : buildPathStats(state.recentLogs);
  const pathMaxDurationStats = Object.keys(state.pathMaxDurationStats).length > 0 ? state.pathMaxDurationStats : buildPathMaxDurationStats(state.recentLogs);
  const search = keyword.trim().toLowerCase();
  return Object.entries(pathDurationStats)
    .map(([path, total]) => ({ path, total, count: pathStats[path] || 0, max: pathMaxDurationStats[path] || 0 }))
    .filter((row) => !search || row.path.toLowerCase().includes(search))
    .sort((left, right) => right.total - left.total || left.path.localeCompare(right.path));
}

function normalizeLimit(value: string): number {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return 50;
  }
  return Math.max(1, Math.min(ROUTE_LOG_MAX_RECENT, Math.floor(number)));
}

function diagnosticKey(entry: ProxyRequestLogEntry): string {
  return [logTimestamp(entry) || '', entry.method || '', normalizedLogPath(entry.path), entry.status || '', entry.durationMs || ''].join('|');
}

function methodBadgeClass(method?: string | null): string {
  switch ((method || '').toUpperCase()) {
    case 'GET':
      return 'method-badge method-get';
    case 'POST':
      return 'method-badge method-post';
    case 'PUT':
    case 'PATCH':
      return 'method-badge method-put';
    case 'DELETE':
      return 'method-badge method-delete';
    default:
      return 'method-badge method-other';
  }
}

function statusBadgeClass(status?: number | null): string {
  const code = Number(status || 0);
  if (code >= 500) return 'status-badge status-5xx';
  if (code >= 400) return 'status-badge status-4xx';
  if (code >= 300) return 'status-badge status-3xx';
  if (code >= 200) return 'status-badge status-2xx';
  return 'status-badge status-none';
}

function logTimestamp(entry: ProxyRequestLogEntry): string | null | undefined {
  return entry.timestamp || entry.time;
}

function diagnosticContextText(entries: ProxyRequestLogEntry[]): string {
  return entries.map((entry, index) => [
    '#' + (index + 1),
    '时间: ' + formatTime(logTimestamp(entry)),
    '方法: ' + (entry.method || '-'),
    '实际访问: ' + (entry.accessAddress || '-'),
    '路径: ' + normalizedLogPath(entry.path),
    '状态: ' + (entry.status || '-'),
    '耗时: ' + formatDuration(entry.durationMs),
    '请求参数: ' + pretty(entry.requestParams),
    '请求体: ' + pretty(entry.requestBody),
    '返回预览: ' + pretty(entry.responseBody),
  ].join('\n')).join('\n\n');
}

function pretty(value?: string | null): string {
  const text = (value || '').trim();
  if (!text) {
    return '-';
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (error) {
    return text;
  }
}
