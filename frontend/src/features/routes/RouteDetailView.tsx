import * as React from 'react';
import { Activity, ArrowLeft, ArrowUpRight, FileJson, Gauge, Globe2, Pencil, Save, ScrollText, Server, Trash2, Waypoints, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { fetchJson } from '@/lib/api';
import { cn, formatDuration, formatTime } from '@/lib/utils';
import { RouteFormPanel } from './RouteFormDialog';
import { RouteTopology } from './RouteTopology';
import { RouteSparkline } from './RouteSparkline';
import type { RouteConfig, RouteConfigPayload, RouteFormValues } from './types';
import type { RouteTrafficMetrics } from './route-metrics';
import { formatCount, formatLatency, normalizeTrafficMetrics, successRatePercent } from './route-metrics';
import {
  deriveRouteStatus,
  effectivePathPrefixes,
  routeAccentClass,
  routeStatusDotClass,
  routeStatusText,
  routeToFormValues,
  visiblePathPrefixes,
} from './route-utils';
import { buildRouteTopologyModel, formatJsonContent, formatJsonText } from './route-detail-utils';
import type { ProxyRequestLogEntry, ProxyRequestLogSnapshot } from '@/features/logs/types';

interface RouteDetailViewProps {
  route: RouteConfig;
  index: number;
  metrics?: RouteTrafficMetrics | null;
  fileName: string;
  content: string;
  loading: boolean;
  error: string;
  existingNames: string[];
  existingBindings: string[];
  onBack: () => void;
  onSaveRoute: (payload: RouteConfigPayload, mode: 'edit', route: RouteConfig) => Promise<void>;
  onAccess: (route: RouteConfig) => void;
  onDelete: (route: RouteConfig) => void;
  onOpenLogs: (route: RouteConfig) => void;
}

const DETAIL_FORM_ID = 'route-detail-config-form';

/**
 * 路由详情：占用路由控制台版面本身，而不是覆盖式的抽屉弹窗。
 *
 * <p>这样详情能与概览带、列表共用同一套栅格与宽度，长内容（拓扑 / 指标 / 日志）
 * 有足够横向空间，也避免了抽屉盖住列表后"看不到自己在看哪一条"的问题。</p>
 */
export function RouteDetailView({
  route,
  index,
  metrics,
  fileName,
  content,
  loading,
  error,
  existingNames,
  existingBindings,
  onBack,
  onSaveRoute,
  onAccess,
  onDelete,
  onOpenLogs,
}: RouteDetailViewProps) {
  const [draftValues, setDraftValues] = React.useState<RouteFormValues | null>(null);
  const [jsonEditing, setJsonEditing] = React.useState(false);
  const [jsonEditValue, setJsonEditValue] = React.useState('');
  const [jsonError, setJsonError] = React.useState('');
  const [recentLogs, setRecentLogs] = React.useState<ProxyRequestLogEntry[]>([]);
  const [tab, setTab] = React.useState('overview');
  const [saving, setSaving] = React.useState(false);
  const routeId = route.id;
  /** 编辑态 = 停在「配置」标签页，底部「编辑」原位换成「保存」。 */
  const editing = tab === 'config';

  React.useEffect(() => {
    setDraftValues(null);
    setJsonEditing(false);
    setJsonEditValue('');
    setJsonError('');
    setTab('overview');
  }, [routeId]);

  React.useEffect(() => {
    let cancelled = false;
    void fetchJson<ProxyRequestLogSnapshot>('/admin/api/proxy-logs/routes/' + encodeURIComponent(routeId))
      .then((snapshot) => { if (!cancelled) setRecentLogs(snapshot.recentLogs || []); })
      .catch(() => { if (!cancelled) setRecentLogs([]); });
    return () => { cancelled = true; };
  }, [routeId]);

  React.useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onBack();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onBack]);

  const liveValues = draftValues || routeToFormValues(route);
  const topology = buildRouteTopologyModel(liveValues);
  const prefixes = effectivePathPrefixes(route);
  const { chips, more } = visiblePathPrefixes(prefixes);
  const status = deriveRouteStatus(route, metrics);
  const traffic = normalizeTrafficMetrics(route.id, metrics);
  const jsonPreview = draftValues ? formatDraftJson(route, draftValues) : content || '{}';
  const jsonDisplayValue = jsonEditing ? jsonEditValue : formatJsonForDisplay(jsonPreview);

  async function handleSave(payload: RouteConfigPayload) {
    setSaving(true);
    try {
      await onSaveRoute(payload, 'edit', route);
      setTab('overview');
    } finally {
      setSaving(false);
    }
  }

  async function saveJsonDraft() {
    try {
      const formatted = formatJsonText(jsonEditValue);
      const parsed = JSON.parse(formatted) as unknown;
      await onSaveRoute(jsonToPayload(parsed), 'edit', route);
      setJsonEditValue(formatted);
      setJsonEditing(false);
      setJsonError('');
    } catch (saveError) {
      setJsonError(saveError instanceof SyntaxError ? 'JSON 格式不正确，请检查后再保存' : saveError instanceof Error ? saveError.message : 'JSON 保存失败');
    }
  }

  return (
    <section className={cn('surface-panel p-4 route-detail-view', routeAccentClass(index))} aria-label={'路由详情 ' + route.name}>
      <header className="detail-head">
        <button type="button" className="btn btn-sm btn-ghost detail-back" onClick={onBack}>
          <ArrowLeft className="h-3.5 w-3.5" aria-hidden="true" />返回列表
        </button>
        <div className="detail-heading">
          <h2 className="detail-title" title={route.name}>{route.name}</h2>
          <span className="detail-meta">
            <span className={cn('dot', routeStatusDotClass(status))} aria-hidden="true" />
            {routeStatusText(status)}
            <span className="detail-file font-mono" title={fileName || route.id + '.json'}>· {fileName || route.id + '.json'}</span>
          </span>
        </div>
        <div className="detail-paths">
          {chips.length === 0
            ? <span className="route-prefix-chip">全部兜底</span>
            : chips.map((prefix) => <span key={prefix} className="route-prefix-chip">{prefix}</span>)}
          {more > 0 && <span className="route-prefix-chip route-prefix-chip-more">+{more}</span>}
        </div>
      </header>

      {error && <div className="alert-error mt-3" role="alert">{error}</div>}

      <Tabs value={tab} onValueChange={setTab} className="flex min-h-0 min-w-0 flex-1 flex-col">
        <TabsList>
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="config">配置</TabsTrigger>
          <TabsTrigger value="logs">日志</TabsTrigger>
          <TabsTrigger value="metrics">指标</TabsTrigger>
        </TabsList>

        <div className="detail-body scroll-area">
          <TabsContent value="overview" className="detail-overview">
            <section className="section detail-span-2">
              <div className="section-head">
                <span className="section-index">1</span>
                <h3 className="section-title">路由拓扑</h3>
              </div>
              <RouteTopology model={topology} idle={!route.enabled} traffic={traffic} />
            </section>

            <section className="section">
              <div className="section-head">
                <span className="section-index">2</span>
                <h3 className="section-title">基本信息</h3>
              </div>
              <div className="field-grid">
                <DetailField label="路由名称" value={liveValues.name || '未命名'} />
                <DetailField label="Path Prefix" value={prefixes.join(' ') || '未配置'} />
                <DetailField label="Target URL" value={liveValues.targetUrl || '未配置'} />
                <DetailField label="代理地址" value={liveValues.accessPageBaseUrl || '未配置'} />
                <DetailField label="本地绑定" value={liveValues.localIp + ':' + (liveValues.localPort || '—')} />
                <DetailField label="访问页" value={liveValues.accessPage || '未配置'} />
              </div>
            </section>

            <section className="section">
              <div className="section-head">
                <span className="section-index">3</span>
                <h3 className="section-title">运行状态</h3>
              </div>
              <div className="detail-metric-row">
                <DetailMetric icon={<Activity className="h-3.5 w-3.5" />} label="请求数" value={formatCount(traffic.totalRequests)} />
                <DetailMetric icon={<Gauge className="h-3.5 w-3.5" />} label="成功率" value={successRatePercent(traffic.totalRequests, traffic.failedRequests)} />
                <DetailMetric icon={<Waypoints className="h-3.5 w-3.5" />} label="平均延迟" value={formatLatency(traffic.averageDurationMs)} />
              </div>
              <div className="detail-spark">
                <RouteSparkline values={traffic.trafficBuckets} idle={!route.enabled} label={route.name + ' 最近 30 分钟请求数'} />
              </div>
            </section>

            <section className="section detail-span-2">
              <div className="section-head">
                <span className="section-index">4</span>
                <h3 className="section-title">最近日志</h3>
                <button type="button" className="btn btn-sm btn-ghost" onClick={() => onOpenLogs(route)}>查看全部</button>
              </div>
              {recentLogs.length === 0 ? (
                <p className="form-hint">暂无代理请求</p>
              ) : (
                <div className="log-rows">
                  {recentLogs.slice(0, 6).map((entry, logIndex) => (
                    <div className="log-row" key={(entry.timestamp || entry.time || '') + logIndex}>
                      <span>{formatTime(entry.timestamp || entry.time)}</span>
                      <span>{entry.method || '-'}</span>
                      <span className="log-row-path" title={entry.path || '/'}>{entry.path || '/'}</span>
                      <span className={statusClass(entry.status)}>{entry.status || '-'}</span>
                      <span>{formatDuration(entry.durationMs)}</span>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </TabsContent>

          <TabsContent value="config" className="grid gap-3">
            <p className="form-hint">表单与 JSON 实时同步；点下方「保存」后立即刷新 Gateway 与本地端口代理。</p>
            <RouteFormPanel
              active
              mode="edit"
              route={route}
              existingNames={existingNames}
              existingBindings={existingBindings}
              onSubmit={handleSave}
              onValuesChange={setDraftValues}
              submitLabel="保存"
              formId={DETAIL_FORM_ID}
              showActions={false}
            />
            <div className="section">
              <div className="section-head">
                <FileJson className="h-3.5 w-3.5" aria-hidden="true" />
                <h3 className="section-title">JSON 配置</h3>
                {jsonEditing ? (
                  <div className="flex gap-2">
                    <Button size="sm" variant="primary" onClick={() => void saveJsonDraft()} disabled={loading}>保存 JSON</Button>
                    <Button size="sm" variant="ghost" onClick={() => { setJsonEditing(false); setJsonError(''); }}>取消</Button>
                  </div>
                ) : (
                  <Button size="sm" variant="outline" onClick={() => { setJsonEditValue(formatJsonForDisplay(jsonPreview)); setJsonError(''); setJsonEditing(true); }} disabled={loading}>编辑 JSON</Button>
                )}
              </div>
              {jsonError && <div className="alert-error" role="alert">{jsonError}</div>}
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
              <p className="form-hint">最近 {recentLogs.length} 条代理请求，完整日志支持实时流与诊断分析。</p>
              <Button size="sm" variant="outline" onClick={() => onOpenLogs(route)}>
                <ScrollText className="h-3.5 w-3.5" />打开完整日志
              </Button>
            </div>
            {recentLogs.length === 0 ? (
              <p className="form-hint">暂无代理请求</p>
            ) : (
              <div className="log-rows">
                {recentLogs.slice(0, 20).map((entry, logIndex) => (
                  <div className="log-row" key={(entry.timestamp || entry.time || '') + logIndex}>
                    <span>{formatTime(entry.timestamp || entry.time)}</span>
                    <span>{entry.method || '-'}</span>
                    <span className="log-row-path" title={entry.path || '/'}>{entry.path || '/'}</span>
                    <span className={statusClass(entry.status)}>{entry.status || '-'}</span>
                    <span>{formatDuration(entry.durationMs)}</span>
                  </div>
                ))}
              </div>
            )}
          </TabsContent>

          <TabsContent value="metrics" className="grid gap-3">
            <div className="detail-metric-row">
              <DetailMetric icon={<Activity className="h-3.5 w-3.5" />} label="请求数 / min" value={formatCount(traffic.requestsLastMinute)} />
              <DetailMetric icon={<Waypoints className="h-3.5 w-3.5" />} label="平均延迟" value={formatLatency(traffic.averageDurationMs)} />
              <DetailMetric icon={<Gauge className="h-3.5 w-3.5" />} label="慢请求" value={formatCount(traffic.slowRequests)} />
            </div>
            <div className="detail-spark">
              <RouteSparkline values={traffic.trafficBuckets} idle={!route.enabled} label={route.name + ' 最近 30 分钟请求数'} />
            </div>
            <div className="field-grid">
              <DetailField label="累计请求数" value={formatCount(traffic.totalRequests)} />
              <DetailField label="失败请求" value={formatCount(traffic.failedRequests)} />
              <DetailField label="成功率" value={successRatePercent(traffic.totalRequests, traffic.failedRequests)} />
              <DetailField label="累计耗时" value={formatDuration(traffic.totalDurationMs)} />
            </div>
          </TabsContent>
        </div>
      </Tabs>

      {/* 操作栏常驻底部：最左侧按钮按状态在「编辑 / 保存」间切换，位置固定在「访问」之前 */}
      <footer className="detail-actions">
        {editing ? (
          <Button type="submit" form={DETAIL_FORM_ID} variant="primary" disabled={saving || loading}>
            <Save className="h-3.5 w-3.5" />{saving ? '保存中…' : '保存'}
          </Button>
        ) : (
          <Button variant="primary" onClick={() => setTab('config')}>
            <Pencil className="h-3.5 w-3.5" />编辑
          </Button>
        )}
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
    </section>
  );
}

function DetailField({ label, value }: { label: string; value: string }) {
  return (
    <div className="field">
      <span className="route-field-label">{label}</span>
      <span className="field-value" title={value}>{value}</span>
    </div>
  );
}

function DetailMetric({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="detail-metric">
      <span className="detail-metric-icon" aria-hidden="true">{icon}</span>
      <span className="detail-metric-body">
        <span className="route-field-label">{label}</span>
        <span className="detail-metric-value">{value}</span>
      </span>
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
