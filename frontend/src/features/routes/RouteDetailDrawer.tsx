import * as React from 'react';
import { ArrowUpRight, FileJson, Pencil, ScrollText, Trash2, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { fetchJson } from '@/lib/api';
import { formatDuration, formatTime } from '@/lib/utils';
import { RouteFormPanel } from './RouteFormDialog';
import { RouteTopology } from './RouteTopology';
import { RouteSparkline } from './RouteSparkline';
import type { RouteConfig, RouteConfigPayload, RouteFormValues } from './types';
import type { RouteTrafficMetrics } from './route-metrics';
import { formatCount, formatLatency, successRatePercent, normalizeTrafficMetrics } from './route-metrics';
import { effectivePathPrefixes, routeAccentClass, deriveRouteStatus, routeStatusDotClass, routeStatusText, routeToFormValues, visiblePathPrefixes } from './route-utils';
import { buildRouteTopologyModel, formatJsonContent, formatJsonText } from './route-detail-utils';
import type { ProxyRequestLogEntry, ProxyRequestLogSnapshot } from '@/features/logs/types';
import { cn } from '@/lib/utils';

interface RouteDetailDrawerProps {
  open: boolean;
  route: RouteConfig | null;
  index: number;
  metrics?: RouteTrafficMetrics | null;
  fileName: string;
  content: string;
  loading: boolean;
  error: string;
  onOpenChange: (open: boolean) => void;
  onSaveRoute: (payload: RouteConfigPayload, mode: 'edit', route: RouteConfig) => Promise<void>;
  existingNames: string[];
  existingBindings: string[];
  onAccess: (route: RouteConfig) => void;
  onDelete: (route: RouteConfig) => void;
  onOpenLogs: (route: RouteConfig) => void;
}

const DETAIL_FORM_ID = 'route-detail-config-form';

export function RouteDetailDrawer({
  open,
  route,
  index,
  metrics,
  fileName,
  content,
  loading,
  error,
  onOpenChange,
  onSaveRoute,
  existingNames,
  existingBindings,
  onAccess,
  onDelete,
  onOpenLogs,
}: RouteDetailDrawerProps) {
  const [draftValues, setDraftValues] = React.useState<RouteFormValues | null>(null);
  const [jsonEditing, setJsonEditing] = React.useState(false);
  const [jsonEditValue, setJsonEditValue] = React.useState('');
  const [jsonError, setJsonError] = React.useState('');
  const [recentLogs, setRecentLogs] = React.useState<ProxyRequestLogEntry[]>([]);
  const [tab, setTab] = React.useState('overview');
  const routeId = route?.id;

  React.useEffect(() => {
    setDraftValues(null);
    setJsonEditing(false);
    setJsonEditValue('');
    setJsonError('');
    setTab('overview');
  }, [routeId, open]);

  React.useEffect(() => {
    if (!open || !routeId) {
      setRecentLogs([]);
      return;
    }
    let cancelled = false;
    void fetchJson<ProxyRequestLogSnapshot>('/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId))
      .then((snapshot) => {
        if (!cancelled) {
          setRecentLogs(snapshot.recentLogs || []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRecentLogs([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [open, routeId]);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onOpenChange(false);
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onOpenChange]);

  if (!open || !route) {
    return null;
  }

  const liveValues = draftValues || routeToFormValues(route);
  const topology = buildRouteTopologyModel(liveValues);
  const prefixes = effectivePathPrefixes(route);
  const { chips, more } = visiblePathPrefixes(prefixes);
  const status = deriveRouteStatus(route, metrics);
  const traffic = normalizeTrafficMetrics(route.id, metrics);
  const jsonPreview = draftValues ? formatDraftJson(route, draftValues) : content || '{}';
  const jsonDisplayValue = jsonEditing ? jsonEditValue : formatJsonForDisplay(jsonPreview);

  async function saveJsonDraft() {
    try {
      const formatted = formatJsonText(jsonEditValue);
      const parsed = JSON.parse(formatted) as unknown;
      const payload = jsonToPayload(parsed);
      await onSaveRoute(payload, 'edit', route!);
      setJsonEditValue(formatted);
      setJsonEditing(false);
      setJsonError('');
    } catch (saveError) {
      setJsonError(saveError instanceof SyntaxError ? 'JSON 格式不正确，请检查后再保存' : saveError instanceof Error ? saveError.message : 'JSON 保存失败');
    }
  }

  return (
    <>
      <button className="route-drawer-scrim" type="button" aria-label="关闭路由详情" onClick={() => onOpenChange(false)} />
      <aside className={cn('route-drawer console-scroll', routeAccentClass(index))} role="dialog" aria-modal="true" aria-labelledby="route-drawer-title">
        <header className="route-drawer-head">
          <div className="route-drawer-title-row">
            <div className="min-w-0 flex-1">
              <h2 id="route-drawer-title" className="route-drawer-title" title={route.name}>{route.name}</h2>
              <span className="route-card-status mt-1">
                <span className={cn('status-dot', routeStatusDotClass(status))} aria-hidden="true" />
                {routeStatusText(status)}
                <span className="console-mono text-console-ink-subtle">· {fileName || route.id + '.json'}</span>
              </span>
            </div>
            <button type="button" className="icon-button" onClick={() => onOpenChange(false)} aria-label="关闭详情">
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="route-drawer-paths">
            {chips.length === 0
              ? <span className="route-prefix-chip">全部兜底</span>
              : chips.map((prefix) => <span key={prefix} className="route-prefix-chip">{prefix}</span>)}
            {more > 0 && <span className="route-prefix-chip route-prefix-chip-more">+{more}</span>}
          </div>
        </header>

        {error && <div className="console-error-box m-3" role="alert">{error}</div>}

        <Tabs value={tab} onValueChange={setTab} className="flex min-h-0 flex-1 flex-col">
          <TabsList>
            <TabsTrigger value="overview">概览</TabsTrigger>
            <TabsTrigger value="config">配置</TabsTrigger>
            <TabsTrigger value="logs">日志</TabsTrigger>
            <TabsTrigger value="metrics">指标</TabsTrigger>
          </TabsList>

          <div className="route-drawer-body console-scroll">
            <TabsContent value="overview" className="grid gap-4">
              <section className="drawer-section">
                <div className="drawer-section-head">
                  <span className="drawer-section-index">1</span>
                  <h3 className="drawer-section-title">路由拓扑</h3>
                </div>
                <RouteTopology model={topology} idle={!route.enabled} />
              </section>

              <section className="drawer-section">
                <div className="drawer-section-head">
                  <span className="drawer-section-index">2</span>
                  <h3 className="drawer-section-title">基本信息</h3>
                </div>
                <div className="drawer-field-grid">
                  <DrawerField label="路由名称" value={liveValues.name || '未命名'} />
                  <DrawerField label="Path Prefix" value={prefixes.join(' ') || '未配置'} />
                  <DrawerField label="Target URL" value={liveValues.targetUrl || '未配置'} />
                  <DrawerField label="代理地址" value={liveValues.accessPageBaseUrl || '未配置'} />
                  <DrawerField label="本地绑定" value={liveValues.localIp + ':' + (liveValues.localPort || '—')} />
                  <DrawerField label="目标类型" value="HTTP" />
                  <DrawerField label="状态" value={routeStatusText(status)} />
                  <DrawerField label="访问页" value={liveValues.accessPage || '未配置'} />
                </div>
              </section>

              <section className="drawer-section">
                <div className="drawer-section-head">
                  <span className="drawer-section-index">3</span>
                  <h3 className="drawer-section-title">运行状态</h3>
                </div>
                <div className="drawer-metrics">
                  <DrawerMetric label="请求数" value={formatCount(traffic.totalRequests)} />
                  <DrawerMetric label="成功率" value={successRatePercent(traffic.totalRequests, traffic.failedRequests)} />
                  <DrawerMetric label="平均延迟" value={formatLatency(traffic.averageDurationMs)} />
                </div>
                <div className="h-[34px]">
                  <RouteSparkline values={traffic.trafficBuckets} idle={!route.enabled} label={`${route.name} 最近 30 分钟请求数`} />
                </div>
              </section>

              <section className="drawer-section">
                <div className="drawer-section-head">
                  <span className="drawer-section-index">4</span>
                  <h3 className="drawer-section-title">最近日志</h3>
                  <button type="button" className="console-button console-button-sm console-button-ghost" onClick={() => onOpenLogs(route)}>查看全部</button>
                </div>
                {recentLogs.length === 0 ? (
                  <p className="drawer-empty">暂无代理请求</p>
                ) : (
                  <div className="drawer-log-list">
                    {recentLogs.slice(0, 5).map((entry, logIndex) => (
                      <div className="drawer-log-row" key={(entry.timestamp || entry.time || '') + logIndex}>
                        <span>{formatTime(entry.timestamp || entry.time)}</span>
                        <span>{entry.method || '-'}</span>
                        <span className="drawer-log-path" title={entry.path || '/'}>{entry.path || '/'}</span>
                        <span className={statusClass(entry.status)}>{entry.status || '-'}</span>
                        <span>{formatDuration(entry.durationMs)}</span>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </TabsContent>

            <TabsContent value="config" className="grid gap-3">
              <div className="flex items-center justify-between gap-3">
                <p className="console-field-hint">左侧表单与 JSON 实时同步；保存后立即刷新 Gateway 与本地端口代理。</p>
                <Button type="submit" form={DETAIL_FORM_ID} variant="primary">保存配置</Button>
              </div>
              <RouteFormPanel
                active={open}
                mode="edit"
                route={route}
                existingNames={existingNames}
                existingBindings={existingBindings}
                onSubmit={(payload) => onSaveRoute(payload, 'edit', route)}
                onValuesChange={setDraftValues}
                submitLabel="保存配置"
                formId={DETAIL_FORM_ID}
                showActions={false}
              />
              <div className="drawer-section">
                <div className="drawer-section-head">
                  <FileJson className="h-3.5 w-3.5 text-console-ink-subtle" aria-hidden="true" />
                  <h3 className="drawer-section-title">JSON 配置</h3>
                  {jsonEditing ? (
                    <div className="flex gap-2">
                      <Button size="sm" variant="primary" onClick={() => void saveJsonDraft()} disabled={loading}>保存 JSON</Button>
                      <Button size="sm" variant="ghost" onClick={() => { setJsonEditing(false); setJsonError(''); }}>取消</Button>
                    </div>
                  ) : (
                    <Button size="sm" variant="outline" onClick={() => { setJsonEditValue(formatJsonForDisplay(jsonPreview)); setJsonError(''); setJsonEditing(true); }} disabled={loading}>编辑 JSON</Button>
                  )}
                </div>
                {jsonError && <div className="console-error-box" role="alert">{jsonError}</div>}
                <Textarea
                  value={loading ? '正在加载配置…' : jsonDisplayValue}
                  onChange={(event) => setJsonEditValue(event.target.value)}
                  readOnly={!jsonEditing || loading}
                  spellCheck={false}
                  aria-label="路由 JSON 配置"
                />
              </div>
            </TabsContent>

            <TabsContent value="logs" className="grid gap-3">
              <div className="flex items-center justify-between gap-3">
                <p className="console-field-hint">最近 {recentLogs.length} 条代理请求，完整日志支持实时流与诊断分析。</p>
                <Button size="sm" variant="outline" onClick={() => onOpenLogs(route)}>
                  <ScrollText className="h-3.5 w-3.5" />打开完整日志
                </Button>
              </div>
              {recentLogs.length === 0 ? (
                <p className="drawer-empty">暂无代理请求</p>
              ) : (
                <div className="drawer-log-list">
                  {recentLogs.slice(0, 20).map((entry, logIndex) => (
                    <div className="drawer-log-row" key={(entry.timestamp || entry.time || '') + logIndex}>
                      <span>{formatTime(entry.timestamp || entry.time)}</span>
                      <span>{entry.method || '-'}</span>
                      <span className="drawer-log-path" title={entry.path || '/'}>{entry.path || '/'}</span>
                      <span className={statusClass(entry.status)}>{entry.status || '-'}</span>
                      <span>{formatDuration(entry.durationMs)}</span>
                    </div>
                  ))}
                </div>
              )}
            </TabsContent>

            <TabsContent value="metrics" className="grid gap-3">
              <div className="drawer-metrics">
                <DrawerMetric label="请求数 / min" value={formatCount(traffic.requestsLastMinute)} />
                <DrawerMetric label="平均延迟" value={formatLatency(traffic.averageDurationMs)} />
                <DrawerMetric label="慢请求" value={formatCount(traffic.slowRequests)} />
              </div>
              <div className="h-[60px]">
                <RouteSparkline values={traffic.trafficBuckets} idle={!route.enabled} label={`${route.name} 最近 30 分钟请求数`} />
              </div>
              <div className="drawer-field-grid">
                <DrawerField label="累计请求数" value={formatCount(traffic.totalRequests)} />
                <DrawerField label="失败请求" value={formatCount(traffic.failedRequests)} />
                <DrawerField label="成功率" value={successRatePercent(traffic.totalRequests, traffic.failedRequests)} />
                <DrawerField label="累计耗时" value={formatDuration(traffic.totalDurationMs)} />
              </div>
            </TabsContent>
          </div>
        </Tabs>

        <footer className="route-drawer-actions">
          <Button variant="primary" onClick={() => setTab('config')}>
            <Pencil className="h-3.5 w-3.5" />编辑
          </Button>
          <Button variant="outline" onClick={() => onAccess(route)}>
            <ArrowUpRight className="h-3.5 w-3.5" />访问
          </Button>
          <Button variant="outline" onClick={() => onOpenLogs(route)}>
            <ScrollText className="h-3.5 w-3.5" />日志
          </Button>
          <Button className="ml-auto" variant="danger" onClick={() => onDelete(route)}>
            <Trash2 className="h-3.5 w-3.5" />删除
          </Button>
        </footer>
      </aside>
    </>
  );
}

function DrawerField({ label, value }: { label: string; value: string }) {
  return (
    <div className="drawer-field">
      <span className="route-field-label">{label}</span>
      <span className="drawer-field-value" title={value}>{value}</span>
    </div>
  );
}

function DrawerMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="drawer-metric">
      <span className="route-field-label">{label}</span>
      <span className="drawer-metric-value">{value}</span>
    </div>
  );
}

function statusClass(status?: number | null): string {
  const code = Number(status || 0);
  if (code >= 500) return 'status-5xx';
  if (code >= 400) return 'status-4xx';
  if (code >= 300) return 'status-3xx';
  if (code >= 200) return 'status-2xx';
  return 'status-none';
}

function normalizePreviewUrl(value?: string | null): string | null {
  const text = (value || '').trim();
  if (!text) {
    return null;
  }
  return /^https?:\/\//.test(text) ? text : 'http://' + text;
}

function formatJsonForDisplay(value: string): string {
  try {
    return JSON.stringify(stripLegacyPreviewFields(JSON.parse(value)), null, 2);
  } catch {
    return value;
  }
}

function stripLegacyPreviewFields(value: unknown): unknown {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return value;
  }
  const routeJson = value as Record<string, unknown>;
  if (!Array.isArray(routeJson.pathPrefixes)) {
    return value;
  }
  const { pathPrefix: _legacyPathPrefix, ...previewJson } = routeJson;
  return previewJson;
}

function jsonToPayload(value: unknown): RouteConfigPayload {
  const routeJson = value as Partial<RouteConfig>;
  const prefixes = Array.isArray(routeJson.pathPrefixes) ? routeJson.pathPrefixes : routeJson.pathPrefix ? [routeJson.pathPrefix] : [];
  return {
    name: String(routeJson.name ?? ''),
    pathPrefix: prefixes[0] || null,
    pathPrefixes: prefixes.filter((prefix): prefix is string => typeof prefix === 'string'),
    targetUrl: String(routeJson.targetUrl ?? ''),
    accessPageBaseUrl: routeJson.accessPageBaseUrl == null ? null : String(routeJson.accessPageBaseUrl),
    accessPage: routeJson.accessPage == null ? null : String(routeJson.accessPage),
    localIp: routeJson.localIp == null ? null : String(routeJson.localIp),
    localPort: typeof routeJson.localPort === 'number' ? routeJson.localPort : routeJson.localPort == null ? null : Number(routeJson.localPort),
    enabled: routeJson.enabled === true,
  };
}

function formatDraftJson(route: RouteConfig, values: RouteFormValues): string {
  const prefixes = values.pathPrefixes || [];
  return JSON.stringify({
    id: route.id,
    name: values.name.trim(),
    pathPrefixes: prefixes,
    targetUrl: normalizePreviewUrl(values.targetUrl) || '',
    accessPageBaseUrl: normalizePreviewUrl(values.accessPageBaseUrl),
    accessPage: values.accessPage.trim() || null,
    localIp: values.localIp.trim() || '127.0.0.1',
    localPort: values.localPort.trim() ? Number(values.localPort.trim()) : null,
    enabled: values.enabled === true,
  }, null, 2);
}
