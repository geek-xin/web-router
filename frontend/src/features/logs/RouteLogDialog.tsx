import * as React from 'react';
import { Eye, Pause, Radio, RefreshCw, Search, X } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { fetchJson } from '@/lib/api';
import { copyText, formatDuration, formatTime } from '@/lib/utils';
import type { RouteConfig } from '@/features/routes/types';
import type { LogViewState, ProxyRequestLogEntry, ProxyRequestLogSnapshot } from './types';
import { addLogEntry, buildDurationTopLogs, buildPathDurationStats, buildPathMaxDurationStats, buildPathStats, errorCount, normalizedLogPath, ROUTE_LOG_MAX_RECENT, slowCount, snapshotToState, successRate } from './log-utils';

interface RouteLogDialogProps {
  open: boolean;
  route: RouteConfig | null;
  onOpenChange: (open: boolean) => void;
}

const initialState: LogViewState = {
  totalRequests: 0,
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
      <DialogContent className="w-[min(98vw,1180px)] bg-[#F8F6F3]">
        <RouteLogPanel open={open} route={route} onClose={() => onOpenChange(false)} />
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface RouteLogPanelProps {
  open: boolean;
  route: RouteConfig | null;
  onClose?: () => void;
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

  const refreshSnapshot = React.useCallback(async (showError = false) => {
    if (!routeId) {
      return;
    }
    try {
      const snapshot = await fetchJson<ProxyRequestLogSnapshot>('/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId));
      setState(snapshotToState(snapshot));
      setStatus('日志快照已更新');
    } catch (error) {
      if (showError) {
        toast.error(error instanceof Error ? error.message : '日志加载失败');
      }
      setStatus('日志刷新失败');
    }
  }, [routeId]);

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
    if (!routeId || !route?.enabled || !window.EventSource || refreshModes[modeIndex].intervalMs !== 0) {
      return;
    }
    const source = new EventSource('/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId) + '/stream');
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
  }, [modeIndex, refreshSnapshot, route?.enabled, routeId, stopStream]);

  React.useEffect(() => {
    if (!open || !routeId) {
      return;
    }
    setState(initialState);
    setDiagnostics([]);
    setDetail(null);
    setActiveTab('realtime');
    void refreshSnapshot(true);
  }, [open, routeId, refreshSnapshot]);

  React.useEffect(() => {
    if (!open || !routeId) {
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
  const failures = errorCount(state.recentLogs);

  function addDiagnostic(entry: ProxyRequestLogEntry) {
    const key = diagnosticKey(entry);
    setDiagnostics((current) => current.some((item) => diagnosticKey(item) === key) ? current : [entry, ...current].slice(0, 20));
    toast.success('已复制到诊断分析');
  }

  return (
    <div className="grid gap-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <Badge variant="indigo" className="mb-3 gap-2"><Radio className="h-4 w-4" />ROUTE TRAFFIC</Badge>
          <h3 className="text-2xl font-black text-clay-ink">路由日志</h3>
          <p className="text-sm font-bold text-clay-muted">{route?.name || '请选择路由'} · {status}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="outline" onClick={() => setModeIndex((index) => (index + 1) % refreshModes.length)}>
            {refreshModes[modeIndex].intervalMs === -1 ? <Pause className="h-4 w-4" /> : <RefreshCw className="h-4 w-4" />}
            {refreshModes[modeIndex].label}
          </Button>
          <Button size="sm" variant="primary" onClick={() => void refreshSnapshot(true)}>立即刷新</Button>
        </div>
      </div>

      <div className="grid gap-3 md:grid-cols-4">
          <Metric label="请求数" value={state.totalRequests} tone="glass-card-gold" />
          <Metric label="失败请求" value={failures} tone="glass-card-blue" />
          <Metric label="慢请求" value={slowCount(state.recentLogs)} tone="glass-card-purple" />
          <Metric label="成功率" value={successRate(state.totalRequests, failures)} tone="glass-card-green" />
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab} className="min-h-0">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <TabsList>
              <TabsTrigger value="realtime">实时日志</TabsTrigger>
              <TabsTrigger value="top">总耗时 Top</TabsTrigger>
              <TabsTrigger value="slow">单耗时 Top</TabsTrigger>
              <TabsTrigger value="diagnostics">诊断分析</TabsTrigger>
            </TabsList>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <label className="relative block min-w-[220px]">
                <span className="sr-only">路径</span>
                <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-clay-ink/50" />
                <Input className="pl-10" value={pathSearch} onChange={(event) => setPathSearch(event.target.value)} type="search" placeholder="搜索路径" />
              </label>
              <label className="flex items-center gap-2 text-sm font-black text-clay-ink">
                显示
                <Input className="w-20" type="number" min={1} max={100} value={limit} onChange={(event) => setLimit(normalizeLimit(event.target.value))} />
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
              <div className="rounded-3xl border-[3px] border-dashed border-clay-border bg-white p-8 text-center font-bold text-clay-muted">
                <strong className="block text-xl font-black">尚未选择请求</strong>
                <span>在「实时日志」或「单耗时 Top」中点击「拷贝分析」，把请求逐条复制到这里形成诊断列表。</span>
              </div>
            ) : (
              <div className="grid gap-3">
                <LogTable logs={diagnostics} emptyText="暂无诊断请求" onDetail={setDetail} onAnalyze={(entry) => setDiagnostics((current) => current.filter((item) => diagnosticKey(item) !== diagnosticKey(entry)))} analyzeLabel="移除" />
              </div>
            )}
          </TabsContent>
        </Tabs>

      {detail && <LogDetailDrawer entry={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}

function Metric({ label, value, tone }: { label: string; value: string | number; tone: string }) {
  return <div className={`${tone} chunky-pressable rounded-[24px] border-[3px] border-clay-border p-4 shadow-clay-sm`}><span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">{label}</span><strong className="mt-1 block text-3xl font-black text-clay-ink">{value}</strong></div>;
}

function PathStatsTable({ rows }: { rows: Array<{ path: string; count: number; total: number; max: number }> }) {
  return (
    <div className="route-log-table-scroll">
      <Table className="min-w-[760px]">
        <TableHeader><TableRow><TableHead className="w-16">序号</TableHead><TableHead>路径</TableHead><TableHead>请求数</TableHead><TableHead>总耗时</TableHead><TableHead>最长单次</TableHead></TableRow></TableHeader>
        <TableBody>
          {rows.length === 0 ? <TableRow><TableCell colSpan={5} className="text-center text-clay-ink/60">暂无请求</TableCell></TableRow> : rows.map((row, index) => (
            <TableRow key={row.path}><TableCell>{index + 1}</TableCell><TableCell className="max-w-[420px] truncate" title={row.path}>{row.path}</TableCell><TableCell>{row.count}</TableCell><TableCell>{formatDuration(row.total)}</TableCell><TableCell>{formatDuration(row.max)}</TableCell></TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function LogTable({ logs, emptyText, durationLabel = '耗时', analyzeLabel = '拷贝分析', onDetail, onAnalyze }: { logs: ProxyRequestLogEntry[]; emptyText: string; durationLabel?: string; analyzeLabel?: string; onDetail: (entry: ProxyRequestLogEntry) => void; onAnalyze: (entry: ProxyRequestLogEntry) => void }) {
  return (
    <div className="route-log-table-scroll">
      <Table className="min-w-[980px]">
        <TableHeader><TableRow><TableHead className="w-16">序号</TableHead><TableHead>时间</TableHead><TableHead>方法</TableHead><TableHead>实际访问</TableHead><TableHead>路径</TableHead><TableHead>状态</TableHead><TableHead>{durationLabel}</TableHead><TableHead>操作</TableHead></TableRow></TableHeader>
        <TableBody>
          {logs.length === 0 ? <TableRow><TableCell colSpan={8} className="text-center text-clay-ink/60">{emptyText}</TableCell></TableRow> : logs.map((entry, index) => (
            <TableRow key={diagnosticKey(entry) + index}>
              <TableCell>{index + 1}</TableCell>
              <TableCell className="whitespace-nowrap">{formatTime(logTimestamp(entry))}</TableCell>
              <TableCell><Badge variant={methodBadgeVariant(entry.method)}>{entry.method || '-'}</Badge></TableCell>
              <TableCell className="max-w-[190px] truncate" title={entry.accessAddress || '-'}>{entry.accessAddress || '-'}</TableCell>
              <TableCell className="max-w-[320px] truncate" title={normalizedLogPath(entry.path)}>{normalizedLogPath(entry.path)}</TableCell>
              <TableCell><Badge variant={Number(entry.status || 0) >= 400 ? 'pink' : 'mint'}>{entry.status || '-'}</Badge></TableCell>
              <TableCell>{formatDuration(entry.durationMs)}</TableCell>
              <TableCell><div className="flex gap-2"><Button size="sm" variant="outline" onClick={() => onDetail(entry)}><Eye className="h-4 w-4" />详情</Button><Button size="sm" variant="outline" onClick={() => onAnalyze(entry)}>{analyzeLabel}</Button></div></TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
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
      <button className="route-log-detail-scrim" type="button" aria-label="关闭请求详情" onClick={onClose} />
      <aside className="route-log-detail-drawer" role="dialog" aria-modal="true" aria-labelledby="route-log-detail-title">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div>
            <Badge variant="indigo" className="mb-2">REQUEST DETAIL</Badge>
            <h3 id="route-log-detail-title" className="text-2xl font-black">请求详情</h3>
          </div>
          <Button ref={closeButtonRef} size="sm" variant="outline" onClick={onClose}><X className="h-4 w-4" />关闭</Button>
        </div>
        <div className="grid gap-3 md:grid-cols-2">
          <Detail label="时间" value={formatTime(logTimestamp(entry))} />
          <Detail label="方法" value={entry.method || '-'} />
          <Detail label="状态" value={String(entry.status || '-')} />
          <Detail label="耗时" value={formatDuration(entry.durationMs)} />
          <Detail label="实际访问" value={entry.accessAddress || '-'} />
        </div>
        <div className="mt-3 grid gap-3">
          <Pre label="路径" value={normalizedLogPath(entry.path)} />
          <Pre label="请求参数" value={pretty(entry.requestParams)} />
          <Pre label="请求体" value={pretty(entry.requestBody)} />
          <Pre label="返回预览" value={pretty(entry.responseBody)} />
        </div>
      </aside>
    </>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return <div className="rounded-2xl border-[3px] border-clay-border bg-clay-cream p-3 shadow-clay-sm"><dt className="text-xs font-black text-clay-ink/60">{label}</dt><dd className="truncate font-black" title={value}>{value}</dd></div>;
}

function Pre({ label, value }: { label: string; value: string }) {
  return <div><h4 className="mb-1 text-sm font-black">{label}</h4><pre className="max-h-36 overflow-auto rounded-2xl border-[3px] border-clay-border bg-white p-3 text-xs font-bold text-clay-ink shadow-clay-sm">{value}</pre></div>;
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

function methodBadgeVariant(method?: string | null): React.ComponentProps<typeof Badge>['variant'] {
  switch ((method || '').toUpperCase()) {
    case 'GET':
      return 'mint';
    case 'POST':
      return 'indigo';
    case 'PUT':
    case 'PATCH':
      return 'yellow';
    case 'DELETE':
      return 'pink';
    default:
      return 'muted';
  }
}

function logTimestamp(entry: ProxyRequestLogEntry): string | null | undefined {
  return entry.timestamp || entry.time;
}

function diagnosticContextText(entries: ProxyRequestLogEntry[]): string {
  return entries.map((entry, index) => [
    `#${index + 1}`,
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
