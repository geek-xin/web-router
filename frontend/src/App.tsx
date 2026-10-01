import * as React from 'react';
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  Bell,
  CircleHelp,
  FileJson,
  Gauge,
  Info,
  LayoutGrid,
  Menu,
  Moon,
  PowerOff,
  Route as RouteIcon,
  ScrollText,
  Search,
  Settings,
  Settings2,
  Sun,
  TriangleAlert,
  Waypoints,
} from 'lucide-react';
import { Toaster, toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { fetchJson, jsonRequest } from '@/lib/api';
import { stripProtocol } from '@/lib/utils';
import { APP_REPOSITORY, APP_VERSION, GATEWAY_ENDPOINT } from '@/lib/app-meta';
import { VersionDialog } from '@/features/system/VersionDialog';
import type { AppVersionInfo } from '@/features/system/types';
import { normalizeVersionInfo, fallbackVersionInfo, platformLabel } from '@/features/system/version-utils';
import type { RouteConfig, RouteConfigPayload, RouteSortKey, RouteStatusFilter } from '@/features/routes/types';
import {
  activeLocalBinding,
  displayTargetUrl,
  effectivePathPrefixes,
  filterRoutes,
  localBinding,
  routeAccessUrl,
  sortRoutes,
} from '@/features/routes/route-utils';
import { formatCompactNumber, formatCount, formatLatency, normalizeTrafficMetrics, type RouteTrafficMetrics } from '@/features/routes/route-metrics';
import { RouteToolbar } from '@/features/routes/RouteToolbar';
import { RouteCard } from '@/features/routes/RouteCard';
import { RouteList } from '@/features/routes/RouteList';
import { RouteSparkline } from '@/features/routes/RouteSparkline';
import {
  applyTheme,
  nextTheme,
  normalizeRouteView,
  normalizeTheme,
  prefersDarkScheme,
  readPreference,
  resolveTheme,
  ROUTE_VIEW_STORAGE_KEY,
  safeStorage,
  THEME_STORAGE_KEY,
  writePreference,
  type RouteViewMode,
  type ThemeMode,
} from '@/lib/preferences';
import { RouteFormDialog } from '@/features/routes/RouteFormDialog';
import { RouteDetailDrawer } from '@/features/routes/RouteDetailDrawer';
import { DeleteConfirmDialog } from '@/features/routes/DeleteConfirmDialog';
import { RouteLogPanel } from '@/features/logs/RouteLogDialog';
import './styles.css';

interface RawConfigResponse {
  fileName: string;
  content: string;
}

interface RouteExportResponse {
  version: number;
  exportedAt: string;
  routes: RouteConfig[];
}

interface RouteImportResponse {
  importedCount: number;
  routes: RouteConfig[];
}

interface DetailDrawerState {
  open: boolean;
  route: RouteConfig | null;
  fileName: string;
  content: string;
  loading: boolean;
  error: string;
}

interface FormState {
  open: boolean;
  mode: 'create' | 'edit' | 'copy';
  route: RouteConfig | null;
}

type WorkspaceView = 'routes' | 'logs';
type GatewayState = 'checking' | 'running' | 'down';

const METRICS_POLL_MS = 5000;

export default function App() {
  const [routes, setRoutes] = React.useState<RouteConfig[]>([]);
  const [metricsById, setMetricsById] = React.useState<Record<string, RouteTrafficMetrics>>({});
  const [gatewayState, setGatewayState] = React.useState<GatewayState>('checking');
  const [loading, setLoading] = React.useState(true);
  const [exporting, setExporting] = React.useState(false);
  const [importing, setImporting] = React.useState(false);
  const [search, setSearch] = React.useState('');
  const [statusFilter, setStatusFilter] = React.useState<RouteStatusFilter>('all');
  const [sortKey, setSortKey] = React.useState<RouteSortKey>('recent');
  const [view, setView] = React.useState<WorkspaceView>('routes');
  const [logRouteId, setLogRouteId] = React.useState<string | null>(null);
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [showConfigPath, setShowConfigPath] = React.useState(false);
  const [versionInfo, setVersionInfo] = React.useState<AppVersionInfo | null>(null);
  const [versionOpen, setVersionOpen] = React.useState(false);
  // 主题与展示形式：用户显式选择会被记住，否则跟随系统 / 默认卡片视图
  const [theme, setTheme] = React.useState<ThemeMode>(() => resolveTheme(normalizeTheme(readPreference(safeStorage(), THEME_STORAGE_KEY)), prefersDarkScheme()));
  const [routeView, setRouteView] = React.useState<RouteViewMode>(() => normalizeRouteView(readPreference(safeStorage(), ROUTE_VIEW_STORAGE_KEY)) ?? 'card');
  const [form, setForm] = React.useState<FormState>({ open: false, mode: 'create', route: null });
  const [detailDrawer, setDetailDrawer] = React.useState<DetailDrawerState>({ open: false, route: null, fileName: '', content: '', loading: false, error: '' });
  const [deleteIds, setDeleteIds] = React.useState<string[]>([]);

  const configPathFull = React.useMemo(() => readConfigPath(), []);

  const loadRoutes = React.useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchJson<RouteConfig[]>('/admin/api/routes');
      setRoutes(data);
      setSelectedIds((current) => current.filter((id) => data.some((route) => route.id === id)));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '路由加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  const loadMetrics = React.useCallback(async () => {
    try {
      const data = await fetchJson<Record<string, Partial<RouteTrafficMetrics>>>('/admin/api/proxy-logs/metrics');
      const next: Record<string, RouteTrafficMetrics> = {};
      for (const [routeId, raw] of Object.entries(data || {})) {
        next[routeId] = normalizeTrafficMetrics(routeId, raw);
      }
      setMetricsById(next);
    } catch {
      // 旧后端没有该接口时保持空指标，界面统一降级为 "-"。
    }
  }, []);

  const loadVersion = React.useCallback(async () => {
    try {
      const raw = await fetchJson<Partial<AppVersionInfo>>('/admin/api/version');
      setVersionInfo(normalizeVersionInfo(raw, fallbackVersionInfo(APP_VERSION, APP_REPOSITORY)));
    } catch {
      setVersionInfo(fallbackVersionInfo(APP_VERSION, APP_REPOSITORY));
    }
  }, []);

  const checkGateway = React.useCallback(async () => {
    try {
      const response = await fetch('/actuator/health', { headers: { Accept: 'application/json' } });
      const body = (await response.json()) as { status?: string };
      setGatewayState(response.ok && body.status === 'UP' ? 'running' : 'down');
    } catch {
      setGatewayState('down');
    }
  }, []);

  React.useEffect(() => {
    void loadRoutes();
    void checkGateway();
    void loadVersion();
  }, [loadRoutes, checkGateway, loadVersion]);

  // 主题：写入 <html data-theme>（CSS 令牌随之切换）
  React.useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  // 未显式选择主题时跟随系统变化
  React.useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return;
    }
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (event: MediaQueryListEvent) => {
      if (normalizeTheme(readPreference(safeStorage(), THEME_STORAGE_KEY))) {
        return;
      }
      setTheme(event.matches ? 'dark' : 'light');
    };
    query.addEventListener('change', handleChange);
    return () => query.removeEventListener('change', handleChange);
  }, []);

  function toggleTheme() {
    setTheme((current) => {
      const next = nextTheme(current);
      writePreference(safeStorage(), THEME_STORAGE_KEY, next);
      return next;
    });
  }

  function changeRouteView(next: RouteViewMode) {
    setRouteView(next);
    writePreference(safeStorage(), ROUTE_VIEW_STORAGE_KEY, next);
  }

  React.useEffect(() => {
    void loadMetrics();
    const timer = window.setInterval(() => void loadMetrics(), METRICS_POLL_MS);
    return () => window.clearInterval(timer);
  }, [loadMetrics]);

  const visibleRoutes = React.useMemo(
    () => sortRoutes(filterRoutes(routes, { keyword: search, status: statusFilter }), sortKey, metricsById),
    [routes, search, statusFilter, sortKey, metricsById],
  );

  const totals = React.useMemo(() => computeTotals(routes, metricsById), [routes, metricsById]);
  const selectedRoutes = React.useMemo(() => routes.filter((route) => selectedIds.includes(route.id)), [routes, selectedIds]);
  const logRoute = React.useMemo(() => routes.find((route) => route.id === logRouteId) || null, [routes, logRouteId]);

  async function handleSaveRoute(payload: RouteConfigPayload, mode: 'create' | 'edit' | 'copy', route?: RouteConfig | null) {
    const url = mode === 'edit' && route ? '/admin/api/routes/' + encodeURIComponent(route.id) : '/admin/api/routes';
    const method = mode === 'edit' ? 'PUT' : 'POST';
    await fetchJson<RouteConfig>(url, jsonRequest(payload, method));
    toast.success(mode === 'edit' ? '路由已更新' : '路由已创建');
    await loadRoutes();
    await loadMetrics();
  }

  async function openDetail(route: RouteConfig) {
    setDetailDrawer({ open: true, route, fileName: route.id + '.json', content: '', loading: true, error: '' });
    try {
      const raw = await fetchJson<RawConfigResponse>('/admin/api/routes/' + encodeURIComponent(route.id) + '/raw');
      setDetailDrawer({ open: true, route, fileName: raw.fileName, content: formatJsonContent(raw.content), loading: false, error: '' });
    } catch (error) {
      setDetailDrawer({ open: true, route, fileName: route.id + '.json', content: '', loading: false, error: error instanceof Error ? error.message : '读取配置失败' });
      toast.error(error instanceof Error ? error.message : '读取配置失败');
    }
  }

  async function saveDetailRoute(payload: RouteConfigPayload, _mode: 'edit', route: RouteConfig) {
    await fetchJson<RouteConfig>('/admin/api/routes/' + encodeURIComponent(route.id), jsonRequest(payload, 'PUT'));
    toast.success('路由已更新');
    await loadRoutes();
    await loadMetrics();
    setDetailDrawer((current) => (current.route && current.route.id === route.id ? { ...current, route: { ...current.route, ...payload } as RouteConfig } : current));
  }

  async function exportRoutes() {
    setExporting(true);
    try {
      const data = await fetchJson<RouteExportResponse>('/admin/api/routes/export');
      downloadJson(data, routeExportFileName(data.exportedAt));
      toast.success(`已导出 ${data.routes.length} 条路由`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '导出失败');
    } finally {
      setExporting(false);
    }
  }

  async function importRoutes(file: File) {
    setImporting(true);
    try {
      const payload = parseRouteImportFile(await file.text());
      const data = await fetchJson<RouteImportResponse>('/admin/api/routes/import', jsonRequest(payload));
      toast.success(`已导入 ${data.importedCount} 条路由`);
      await loadRoutes();
      await loadMetrics();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '导入失败');
    } finally {
      setImporting(false);
    }
  }

  async function toggleRoute(route: RouteConfig) {
    if (!route.enabled && !route.localPort) {
      toast.error('请先编辑路由并填写监听端口后再启用');
      return;
    }
    try {
      const payload = routeToPayload({ ...route, enabled: !route.enabled });
      await fetchJson<RouteConfig>('/admin/api/routes/' + encodeURIComponent(route.id), jsonRequest(payload, 'PUT'));
      toast.success(route.enabled ? '路由已停用' : '路由已启用');
      await loadRoutes();
      await loadMetrics();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '状态更新失败');
    }
  }

  async function deleteRoutes(ids: string[]) {
    try {
      for (const id of ids) {
        await fetchJson<void>('/admin/api/routes/' + encodeURIComponent(id), { method: 'DELETE' });
      }
      toast.success(ids.length > 1 ? '选中路由已删除' : '路由已删除');
      setSelectedIds([]);
      setDeleteIds([]);
      if (detailDrawer.route && ids.includes(detailDrawer.route.id)) {
        setDetailDrawer((current) => ({ ...current, open: false, route: null }));
      }
      await loadRoutes();
      await loadMetrics();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '删除失败');
    }
  }

  function openAccess(route: RouteConfig) {
    const url = routeAccessUrl({ localBinding: activeLocalBinding(route), accessPage: route.accessPage, pathPrefixes: effectivePathPrefixes(route) });
    if (!url) {
      toast.error('请先启用路由并填写监听端口和访问页');
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }

  function openLogs(route: RouteConfig) {
    setLogRouteId(route.id);
    setView('logs');
  }

  return (
    <>
      <NetworkBackdrop />
      <div className="console-shell">
        <ConsoleSidebar
          routeCount={routes.length}
          gatewayState={gatewayState}
          view={view}
          onViewChange={setView}
          configPathFull={configPathFull}
          showConfigPath={showConfigPath}
          onToggleConfigPath={() => setShowConfigPath((value) => !value)}
          onExport={() => void exportRoutes()}
          onImport={(file) => void importRoutes(file)}
          exporting={exporting}
          importing={importing}
          version={versionInfo?.version || APP_VERSION}
          platform={versionInfo?.platform || 'unknown'}
          onOpenVersion={() => setVersionOpen(true)}
        />

        <div className="console-main">
          <ConsoleTopbar
            gatewayState={gatewayState}
            version={versionInfo?.version || APP_VERSION}
            theme={theme}
            onToggleTheme={toggleTheme}
            onSearchFocus={() => setView('routes')}
            onOpenVersion={() => setVersionOpen(true)}
          />

          <div className="console-workspace">
            {view === 'routes' ? (
              <>
                <section className="kpi-row" aria-label="路由概览">
                  <KpiCard label="路由总数" value={formatCount(totals.routeCount)} active={statusFilter === 'all'} onClick={() => setStatusFilter('all')} icon={<RouteIcon className="h-3.5 w-3.5" />} />
                  <KpiCard label="运行中" value={formatCount(totals.enabledCount)} active={statusFilter === 'enabled'} onClick={() => setStatusFilter('enabled')} icon={<Activity className="h-3.5 w-3.5" />} tone="success" />
                  <KpiCard label="已停用" value={formatCount(totals.disabledCount)} active={statusFilter === 'disabled'} onClick={() => setStatusFilter('disabled')} icon={<PowerOff className="h-3.5 w-3.5" />} />
                  <KpiCard
                    label="请求数 / 分钟"
                    value={formatCompactNumber(totals.requestsLastMinute)}
                    icon={<Gauge className="h-3.5 w-3.5" />}
                    spark={totals.trafficBuckets}
                  />
                  <KpiCard label="平均延迟" value={formatLatency(totals.averageDurationMs)} icon={<Waypoints className="h-3.5 w-3.5" />} />
                </section>

                <section className="console-panel p-3.5" aria-labelledby="route-config-heading">
                  <RouteToolbar
                    headingId="route-config-heading"
                    search={search}
                    status={statusFilter}
                    sort={sortKey}
                    selectedCount={selectedIds.length}
                    viewMode={routeView}
                    onViewModeChange={changeRouteView}
                    onSearchChange={setSearch}
                    onStatusChange={setStatusFilter}
                    onSortChange={setSortKey}
                    onAdd={() => setForm({ open: true, mode: 'create', route: null })}
                    onExport={() => void exportRoutes()}
                    onImport={(file) => void importRoutes(file)}
                    onBatchDelete={() => setDeleteIds(selectedIds)}
                    exporting={exporting}
                    importing={importing}
                  />

                  {loading ? (
                    <div className={`route-card-board mt-3.5 ${detailDrawer.open ? 'route-card-board-with-drawer' : ''}`} aria-live="polite">
                      <SkeletonCards />
                    </div>
                  ) : visibleRoutes.length === 0 ? (
                    <div className="console-empty mt-3.5" aria-live="polite">
                      <RouteIcon className="h-5 w-5 text-console-ink-subtle" aria-hidden="true" />
                      <strong className="console-empty-title">还没有匹配的路由</strong>
                      <span className="console-empty-copy">
                        {routes.length === 0
                          ? '点击「新增路由」配置默认地址、监听端口和访问页，按需添加路径前缀。'
                          : '当前筛选条件下没有路由，试着切换状态筛选或清空搜索关键词。'}
                      </span>
                      <Button variant="primary" onClick={() => setForm({ open: true, mode: 'create', route: null })}>新增路由</Button>
                    </div>
                  ) : routeView === 'card' ? (
                    <div className={`route-card-board mt-3.5 ${detailDrawer.open ? 'route-card-board-with-drawer' : ''}`} aria-live="polite">
                      {visibleRoutes.map((route, index) => (
                        <RouteCard
                          key={route.id}
                          route={route}
                          index={index}
                          selected={selectedIds.includes(route.id)}
                          metrics={metricsById[route.id]}
                          onSelectedChange={(selected) => setSelectedIds((current) => selected ? Array.from(new Set([...current, route.id])) : current.filter((id) => id !== route.id))}
                          onView={() => void openDetail(route)}
                          onCopy={() => setForm({ open: true, mode: 'copy', route })}
                          onLogs={() => openLogs(route)}
                          onAccess={() => openAccess(route)}
                          onToggle={() => void toggleRoute(route)}
                          onDelete={() => setDeleteIds([route.id])}
                        />
                      ))}
                    </div>
                  ) : (
                    <div className="mt-3.5" aria-live="polite">
                      <RouteList
                        routes={visibleRoutes}
                        metricsById={metricsById}
                        selectedIds={selectedIds}
                        onSelectedChange={(routeId, selected) => setSelectedIds((current) => selected ? Array.from(new Set([...current, routeId])) : current.filter((id) => id !== routeId))}
                        onView={(route) => void openDetail(route)}
                        onCopy={(route) => setForm({ open: true, mode: 'copy', route })}
                        onLogs={(route) => openLogs(route)}
                        onAccess={(route) => openAccess(route)}
                        onToggle={(route) => void toggleRoute(route)}
                        onDelete={(route) => setDeleteIds([route.id])}
                      />
                    </div>
                  )}

                  <p className="mt-3.5 text-[11.5px] text-console-ink-subtle">
                    共 {routes.length} 条路由，当前显示 {visibleRoutes.length} 条{selectedIds.length > 0 ? `，已选 ${selectedIds.length} 条` : ''}。
                  </p>
                </section>
              </>
            ) : (
              <section className="console-panel p-3.5" aria-label="请求日志">
                <div className="route-toolbar mb-3.5">
                  <div className="route-toolbar-heading">
                    <span className="route-toolbar-eyebrow">Request Monitor</span>
                    <h2 className="route-toolbar-title">请求日志</h2>
                  </div>
                  <label className="console-search">
                    <span className="sr-only">选择路由</span>
                    <Search className="console-search-icon" aria-hidden="true" />
                    <select
                      className="console-select w-full"
                      value={logRouteId || ''}
                      onChange={(event) => setLogRouteId(event.target.value || null)}
                      aria-label="选择要查看日志的路由"
                    >
                      <option value="">全部路由（选择一条查看明细）</option>
                      {routes.map((route) => (
                        <option key={route.id} value={route.id}>{route.name} · {localBinding(route.localIp, route.localPort) || '未配置端口'}</option>
                      ))}
                    </select>
                  </label>
                  <Button variant="outline" onClick={() => setView('routes')}>返回路由列表</Button>
                </div>
                {logRoute ? (
                  <RouteLogPanel open route={logRoute} />
                ) : (
                  <div className="console-empty">
                    <ScrollText className="h-5 w-5 text-console-ink-subtle" aria-hidden="true" />
                    <strong className="console-empty-title">请选择一条路由</strong>
                    <span className="console-empty-copy">日志按路由归集，选择上方路由即可查看实时流、耗时 Top 与诊断分析。</span>
                  </div>
                )}
              </section>
            )}
          </div>
        </div>
      </div>

      <RouteFormDialog
        open={form.open}
        mode={form.mode}
        route={form.route}
        existingNames={routes.map((route) => route.name)}
        existingBindings={routes.map(activeLocalBinding).filter(Boolean)}
        onOpenChange={(open) => setForm((current) => ({ ...current, open }))}
        onSubmit={handleSaveRoute}
      />
      <DeleteConfirmDialog
        open={deleteIds.length > 0}
        names={routes.filter((route) => deleteIds.includes(route.id)).map((route) => route.name)}
        onOpenChange={(open) => !open && setDeleteIds([])}
        onConfirm={() => void deleteRoutes(deleteIds)}
      />
      <RouteDetailDrawer
        open={detailDrawer.open}
        route={detailDrawer.route}
        index={Math.max(0, visibleRoutes.findIndex((route) => route.id === detailDrawer.route?.id))}
        metrics={detailDrawer.route ? metricsById[detailDrawer.route.id] : null}
        fileName={detailDrawer.fileName}
        content={detailDrawer.content}
        loading={detailDrawer.loading}
        error={detailDrawer.error}
        onOpenChange={(open) => setDetailDrawer((current) => ({ ...current, open }))}
        onSaveRoute={saveDetailRoute}
        existingNames={routes.map((route) => route.name)}
        existingBindings={routes.map(activeLocalBinding).filter(Boolean)}
        onAccess={openAccess}
        onDelete={(route) => setDeleteIds([route.id])}
        onOpenLogs={openLogs}
      />
      <VersionDialog
        open={versionOpen}
        onOpenChange={setVersionOpen}
        versionFallback={APP_VERSION}
        repositoryFallback={APP_REPOSITORY}
      />
      <Toaster richColors position="top-right" />
    </>
  );
}

interface Totals {
  routeCount: number;
  enabledCount: number;
  disabledCount: number;
  requestsLastMinute: number;
  averageDurationMs: number;
  trafficBuckets: number[];
}

function computeTotals(routes: RouteConfig[], metricsById: Record<string, RouteTrafficMetrics>): Totals {
  const enabledCount = routes.filter((route) => route.enabled).length;
  let requestsLastMinute = 0;
  let totalRequests = 0;
  let totalDurationMs = 0;
  const buckets = new Array<number>(30).fill(0);

  for (const route of routes) {
    const metrics = metricsById[route.id];
    if (!metrics) {
      continue;
    }
    requestsLastMinute += metrics.requestsLastMinute;
    totalRequests += metrics.totalRequests;
    totalDurationMs += metrics.totalDurationMs;
    metrics.trafficBuckets.forEach((value, index) => {
      if (index < buckets.length) {
        buckets[index] += value;
      }
    });
  }

  return {
    routeCount: routes.length,
    enabledCount,
    disabledCount: routes.length - enabledCount,
    requestsLastMinute,
    averageDurationMs: totalRequests > 0 ? Math.round(totalDurationMs / totalRequests) : 0,
    trafficBuckets: buckets,
  };
}

function KpiCard({ label, value, icon, active = false, onClick, spark, tone }: { label: string; value: string; icon: React.ReactNode; active?: boolean; onClick?: () => void; spark?: number[]; tone?: 'success' }) {
  function handleKeyDown(event: React.KeyboardEvent<HTMLDivElement>) {
    if (!onClick) {
      return;
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick();
    }
  }

  return (
    <div
      className={`kpi-card${active ? ' kpi-card-active' : ''}`}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-pressed={onClick ? active : undefined}
      aria-label={onClick ? `${label}：${value}，点击筛选` : `${label}：${value}`}
      onClick={onClick}
      onKeyDown={handleKeyDown}
    >
      <div className="kpi-head">
        <span className="kpi-label">{label}</span>
        <span className={tone === 'success' ? 'text-console-success' : 'text-console-ink-subtle'} aria-hidden="true">{icon}</span>
      </div>
      <div className="flex items-baseline gap-2">
        <strong className="kpi-value">{value}</strong>
        {active && <span className="kpi-filter-badge">当前筛选</span>}
      </div>
      {spark && (
        <div className="kpi-spark accent-cyan">
          <RouteSparkline values={spark} label="全局最近 30 分钟请求数" />
        </div>
      )}
    </div>
  );
}

function ConsoleTopbar({ gatewayState, version, theme, onToggleTheme, onSearchFocus, onOpenVersion }: { gatewayState: GatewayState; version: string; theme: ThemeMode; onToggleTheme: () => void; onSearchFocus: () => void; onOpenVersion: () => void }) {
  const running = gatewayState === 'running';
  return (
    <header className="console-topbar">
      <div className="min-w-0">
        <h1 className="topbar-title">路由管理</h1>
        <p className="topbar-subtitle">管理本地路由、代理端口和请求转发</p>
      </div>
      <div className="topbar-actions">
        <button type="button" className="version-pill" onClick={onOpenVersion} aria-label={`当前版本 v${version}，查看版本与更新`} title="版本与更新">
          <span className="console-mono">v{version}</span>
        </button>
        <span className="gateway-pill" title={running ? 'Gateway 运行中' : gatewayState === 'checking' ? '正在检查 Gateway 状态' : 'Gateway 未响应'}>
          <span className={`status-dot ${running ? 'status-running' : gatewayState === 'checking' ? 'status-warning' : 'status-error'}`} aria-hidden="true" />
          {running ? 'Gateway Running' : gatewayState === 'checking' ? 'Checking' : 'Gateway Down'}
          <span className="gateway-pill-address">{GATEWAY_ENDPOINT}</span>
        </span>
        <button
          type="button"
          className="icon-button"
          onClick={onToggleTheme}
          aria-label={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
          title={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
        >
          {theme === 'dark' ? <Sun className="h-3.5 w-3.5" aria-hidden="true" /> : <Moon className="h-3.5 w-3.5" aria-hidden="true" />}
        </button>
        <button type="button" className="icon-button" onClick={onSearchFocus} aria-label="搜索路由"><Search className="h-3.5 w-3.5" /></button>
        <button type="button" className="icon-button" aria-label="通知"><Bell className="h-3.5 w-3.5" /></button>
        <button type="button" className="icon-button" aria-label="设置"><Settings className="h-3.5 w-3.5" /></button>
        <span className="topbar-avatar" aria-hidden="true">WR</span>
      </div>
    </header>
  );
}

interface SidebarProps {
  routeCount: number;
  gatewayState: GatewayState;
  view: WorkspaceView;
  onViewChange: (view: WorkspaceView) => void;
  configPathFull: string;
  showConfigPath: boolean;
  onToggleConfigPath: () => void;
  onExport: () => void;
  onImport: (file: File) => void;
  exporting: boolean;
  importing: boolean;
  version: string;
  platform: string;
  onOpenVersion: () => void;
}

function ConsoleSidebar({ routeCount, gatewayState, view, onViewChange, configPathFull, showConfigPath, onToggleConfigPath, onExport, onImport, exporting, importing, version, platform, onOpenVersion }: SidebarProps) {
  const importRef = React.useRef<HTMLInputElement>(null);
  const running = gatewayState === 'running';

  return (
    <aside className="console-sidebar console-scroll">
      <div className="side-brand">
        <span className="side-brand-mark" aria-hidden="true">
          <RouteNodeMark />
        </span>
        <span className="side-brand-text">
          <span className="side-brand-title">WROUTER</span>
          <span className="side-brand-subtitle">Local Gateway Console</span>
        </span>
      </div>

      <nav className="side-nav" aria-label="主导航">
        <button type="button" className={`side-nav-item${view === 'routes' ? ' side-nav-item-active' : ''}`} onClick={() => onViewChange('routes')}>
          <LayoutGrid className="side-nav-icon" aria-hidden="true" />
          <span className="side-nav-label">路由管理</span>
          <span className="side-badge">{routeCount}</span>
        </button>
        <button type="button" className={`side-nav-item${view === 'logs' ? ' side-nav-item-active' : ''}`} onClick={() => onViewChange('logs')}>
          <ScrollText className="side-nav-icon" aria-hidden="true" />
          <span className="side-nav-label">日志</span>
        </button>
      </nav>

      <div>
        <p className="side-group-label">运行状态</p>
        <div className="side-nav">
          <div className="side-status-row">
            <span className={`status-dot ${running ? 'status-running' : 'status-error'}`} aria-hidden="true" />
            <span>Gateway</span>
          </div>
          <div className="side-status-row">
            <span className={`status-dot ${running ? 'status-running' : 'status-stopped'}`} aria-hidden="true" />
            <span>Local Proxy</span>
          </div>
        </div>
      </div>

      <div>
        <p className="side-group-label">系统设置</p>
        <div className="side-nav">
          <button type="button" className="side-nav-item" onClick={onToggleConfigPath} aria-expanded={showConfigPath}>
            <Settings2 className="side-nav-icon" aria-hidden="true" />
            <span className="side-nav-label">配置管理</span>
          </button>
          <button type="button" className="side-nav-item" onClick={onExport} disabled={exporting}>
            <FileJson className="side-nav-icon" aria-hidden="true" />
            <span className="side-nav-label">{exporting ? '导出中…' : '导出配置'}</span>
          </button>
          <input
            ref={importRef}
            className="hidden"
            type="file"
            accept="application/json,.json"
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = '';
              if (file) {
                onImport(file);
              }
            }}
          />
          <button type="button" className="side-nav-item" onClick={() => importRef.current?.click()} disabled={importing}>
            <ArrowUpRight className="side-nav-icon" aria-hidden="true" />
            <span className="side-nav-label">{importing ? '导入中…' : '导入配置'}</span>
          </button>
          <button type="button" className="side-nav-item" onClick={onOpenVersion}>
            <CircleHelp className="side-nav-icon" aria-hidden="true" />
            <span className="side-nav-label">关于</span>
          </button>
        </div>
      </div>

      {showConfigPath && (
        <div className="runtime-card">
          <span className="side-group-label" style={{ padding: 0 }}>配置目录</span>
          {configPathFull
            ? <span className="runtime-card-address" title={configPathFull} style={{ whiteSpace: 'normal', wordBreak: 'break-all' }}>{configPathFull}</span>
            : <span className="runtime-card-address">配置目录已隐藏</span>}
        </div>
      )}

      <div className="side-spacer" />

      <div className="runtime-card">
        <div className="runtime-card-head">
          <span className={`status-dot ${running ? 'status-running' : 'status-error'}`} aria-hidden="true" />
          {running ? '网关运行中' : gatewayState === 'checking' ? '正在检查网关' : '网关未响应'}
        </div>
        <span className="runtime-card-address">{GATEWAY_ENDPOINT}</span>
        <button type="button" className="runtime-card-version runtime-card-version-button" onClick={onOpenVersion} title={`${platformLabel(platform)} · 查看版本与更新`}>
          v{version} · {platformLabel(platform)}
        </button>
      </div>
    </aside>
  );
}

function SkeletonCards() {
  return (
    <>
      {[0, 1, 2, 3, 4, 5].map((index) => (
        <div key={index} className="console-skeleton h-[190px]" aria-hidden="true" />
      ))}
      <span className="sr-only">正在加载路由…</span>
    </>
  );
}

/** 品牌背景装饰：极淡的网络节点与连线，见设计系统 §7.2。 */
function NetworkBackdrop() {
  return (
    <div className="network-backdrop" aria-hidden="true">
      <svg className="network-backdrop-left" viewBox="0 0 620 460" fill="none" stroke="currentColor" strokeWidth="1.2">
        <path d="M60 380 L200 300 L340 350 L470 240" />
        <path d="M200 300 L230 180 L370 130 L470 240" />
        <path d="M60 380 L120 240 L230 180" />
        <circle cx="60" cy="380" r="7" />
        <circle cx="200" cy="300" r="7" />
        <circle cx="340" cy="350" r="7" />
        <circle cx="470" cy="240" r="7" />
        <circle cx="120" cy="240" r="7" />
        <circle cx="230" cy="180" r="7" />
        <circle cx="370" cy="130" r="7" />
      </svg>
      <svg className="network-backdrop-right" viewBox="0 0 620 460" fill="none" stroke="currentColor" strokeWidth="1.2">
        <path d="M80 120 L220 200 L360 140 L500 230" />
        <path d="M220 200 L260 330 L400 380 L500 230" />
        <circle cx="80" cy="120" r="7" />
        <circle cx="220" cy="200" r="7" />
        <circle cx="360" cy="140" r="7" />
        <circle cx="500" cy="230" r="7" />
        <circle cx="260" cy="330" r="7" />
        <circle cx="400" cy="380" r="7" />
      </svg>
    </div>
  );
}

function RouteNodeMark() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="#4DA3FF" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
      <path d="M6 7h12" />
      <path d="M6 7l6 10" />
      <path d="M18 7l-6 10" />
      <circle cx="6" cy="7" r="2.4" fill="#07111F" />
      <circle cx="18" cy="7" r="2.4" fill="#07111F" stroke="#10D9A0" />
      <circle cx="12" cy="17" r="2.4" fill="#07111F" />
    </svg>
  );
}

function routeToPayload(route: RouteConfig): RouteConfigPayload {
  const prefixes = effectivePathPrefixes(route);
  return {
    name: route.name,
    pathPrefix: prefixes[0] || null,
    pathPrefixes: prefixes,
    targetUrl: stripProtocol(route.targetUrl),
    accessPageBaseUrl: route.accessPageBaseUrl ? stripProtocol(route.accessPageBaseUrl) : null,
    accessPage: route.accessPage || null,
    localIp: route.localIp || '127.0.0.1',
    localPort: route.localPort || null,
    enabled: route.enabled,
  };
}

function readConfigPath(): string {
  return document.querySelector<HTMLMetaElement>('meta[name="routes-config-dir"]')?.content.trim() || '';
}

function downloadJson(data: unknown, fileName: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function formatJsonContent(content: string): string {
  try {
    return JSON.stringify(JSON.parse(content), null, 2);
  } catch {
    return content;
  }
}

function routeExportFileName(exportedAt: string): string {
  const date = new Date(exportedAt);
  const stamp = [
    date.getUTCFullYear(),
    pad(date.getUTCMonth() + 1),
    pad(date.getUTCDate()),
    '-',
    pad(date.getUTCHours()),
    pad(date.getUTCMinutes()),
    pad(date.getUTCSeconds()),
  ].join('');
  return `wrouter-routes-${stamp}.json`;
}

function parseRouteImportFile(content: string): { version: number; routes: RouteConfigPayload[] } {
  let value: unknown;
  try {
    value = JSON.parse(content);
  } catch {
    throw new Error('导入文件不是有效 JSON');
  }
  if (Array.isArray(value)) {
    return { version: 1, routes: value as RouteConfigPayload[] };
  }
  if (!value || typeof value !== 'object' || !Array.isArray((value as { routes?: unknown }).routes)) {
    throw new Error('导入文件缺少 routes 数组');
  }
  const record = value as { version?: unknown; routes: RouteConfigPayload[] };
  return { version: typeof record.version === 'number' ? record.version : 1, routes: record.routes };
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}
