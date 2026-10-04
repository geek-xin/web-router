import * as React from 'react';
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  Bell,
  ChevronDown,
  ChevronUp,
  CircleHelp,
  Download,
  FileJson,
  Gauge,
  LayoutGrid,
  List,
  Moon,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  PowerOff,
  Route as RouteIcon,
  ScrollText,
  Search,
  Settings2,
  Sun,
  Trash2,
  Upload,
  Waypoints,
} from 'lucide-react';
import { Toaster, toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input, Select } from '@/components/ui/input';
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
import { RouteCard } from '@/features/routes/RouteCard';
import { RouteList } from '@/features/routes/RouteList';
import { RouteToolbar } from '@/features/routes/RouteToolbar';
import {
  applyTheme,
  bandToggleLabel,
  nextBandMode,
  nextSidebarMode,
  nextTheme,
  normalizeBandMode,
  normalizeRouteView,
  normalizeSidebarMode,
  normalizeTheme,
  prefersDarkScheme,
  readPreference,
  BAND_STORAGE_KEY,
  resolveBandMode,
  resolveSidebarMode,
  resolveTheme,
  ROUTE_VIEW_STORAGE_KEY,
  safeStorage,
  SIDEBAR_STORAGE_KEY,
  sidebarToggleLabel,
  THEME_STORAGE_KEY,
  writePreference,
  type BandMode,
  type RouteViewMode,
  type SidebarMode,
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
  const [sidebarMode, setSidebarMode] = React.useState<SidebarMode>(() => resolveSidebarMode(normalizeSidebarMode(readPreference(safeStorage(), SIDEBAR_STORAGE_KEY))));
  const [bandMode, setBandMode] = React.useState<BandMode>(() => resolveBandMode(normalizeBandMode(readPreference(safeStorage(), BAND_STORAGE_KEY))));
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

  function toggleSidebar() {
    setSidebarMode((current) => {
      const next = nextSidebarMode(current);
      writePreference(safeStorage(), SIDEBAR_STORAGE_KEY, next);
      return next;
    });
  }

  function toggleBand() {
    setBandMode((current) => {
      const next = nextBandMode(current);
      writePreference(safeStorage(), BAND_STORAGE_KEY, next);
      return next;
    });
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
      toast.success('已导出 ' + data.routes.length + ' 条路由');
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
      toast.success('已导入 ' + data.importedCount + ' 条路由');
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
      <div className="app-canvas">
        <div className={sidebarMode === 'collapsed' ? 'app-shell app-shell-collapsed' : 'app-shell'}>
          <AppSidebar
            mode={sidebarMode}
            onToggleMode={toggleSidebar}
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

          <div className="app-main">
            <AppTopbar
              gatewayState={gatewayState}
              version={versionInfo?.version || APP_VERSION}
              theme={theme}
              search={search}
              onSearchChange={(value) => { setSearch(value); setView('routes'); }}
              onToggleTheme={toggleTheme}
              onOpenVersion={() => setVersionOpen(true)}
            />

            <div className="app-content scroll-area">
              {view === 'routes' ? (
                <>
                  <OverviewBand
                    totals={totals}
                    statusFilter={statusFilter}
                    onStatusChange={setStatusFilter}
                    mode={bandMode}
                    onToggleMode={toggleBand}
                  />

                  <section className="surface-panel p-4" aria-labelledby="route-panel-title">
                    <RouteToolbar
                      headingId="route-panel-title"
                      routeCount={routes.length}
                      visibleCount={visibleRoutes.length}
                      status={statusFilter}
                      sort={sortKey}
                      selectedCount={selectedIds.length}
                      viewMode={routeView}
                      onViewModeChange={changeRouteView}
                      onStatusChange={setStatusFilter}
                      onSortChange={setSortKey}
                      onAdd={() => setForm({ open: true, mode: 'create', route: null })}
                      onExport={() => void exportRoutes()}
                      onImport={(file: File) => void importRoutes(file)}
                      onBatchDelete={() => setDeleteIds(selectedIds)}
                      exporting={exporting}
                      importing={importing}
                    />

                    {/* 只有这一块滚动：工具栏与下方统计行保持固定，见设计系统 §5.5 */}
                    <div className="panel-scroll scroll-area">
                      {loading ? (
                        <div className="route-card-board mt-4" aria-live="polite">
                          <SkeletonCards />
                        </div>
                      ) : visibleRoutes.length === 0 ? (
                        <div className="empty-state" aria-live="polite">
                          <RouteIcon className="h-5 w-5" aria-hidden="true" />
                          <strong className="empty-title">还没有匹配的路由</strong>
                          <span className="empty-copy">
                            {routes.length === 0
                              ? '点击「新增路由」配置默认地址、监听端口和访问页，按需添加路径前缀。'
                              : '当前筛选条件下没有路由，试着切换状态筛选或清空搜索关键词。'}
                          </span>
                          <Button variant="primary" onClick={() => setForm({ open: true, mode: 'create', route: null })}>新增路由</Button>
                        </div>
                      ) : routeView === 'card' ? (
                        <div className="route-card-board mt-4" aria-live="polite">
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
                        <div className="mt-4" aria-live="polite">
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
                    </div>

                    <p className="panel-footnote text-[11.5px]">
                      共 {routes.length} 条路由，当前显示 {visibleRoutes.length} 条{selectedIds.length > 0 ? '，已选 ' + selectedIds.length + ' 条' : ''}。
                    </p>
                  </section>
                </>
              ) : (
                <section className="surface-panel p-4" aria-label="请求日志">
                  <div className="toolbar mb-4">
                    <div className="toolbar-heading">
                      <h2 className="toolbar-title">请求日志</h2>
                      <p className="toolbar-note">默认展示全部路由日志，可按路由筛选；支持实时流、耗时 Top 与诊断分析</p>
                    </div>
                    <label className="search-field">
                      <span className="sr-only">选择路由</span>
                      <Search className="search-field-icon" aria-hidden="true" />
                      <select
                        className="field-select w-full"
                        value={logRouteId || ''}
                        onChange={(event) => setLogRouteId(event.target.value || null)}
                        aria-label="选择要查看日志的路由"
                      >
                        <option value="">全部路由（聚合所有日志）</option>
                        {routes.map((route) => (
                          <option key={route.id} value={route.id}>{route.name} · {localBinding(route.localIp, route.localPort) || '未配置端口'}</option>
                        ))}
                      </select>
                    </label>
                    <Button variant="outline" onClick={() => setView('routes')}>返回路由列表</Button>
                  </div>
                  {/* 日志面板同样只让内部区域滚动，页面本身不出现滚动条。
                      route 为 null 时展示全部路由的聚合日志，不需要先选一条。 */}
                  <div className="panel-scroll scroll-area">
                    <RouteLogPanel open route={logRoute} />
                  </div>
                </section>
              )}
            </div>
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
  requestsInWindow: number;
  peakPerMinute: number;
  averageDurationMs: number;
  trafficBuckets: number[];
  deltaPercent: number | null;
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

  const requestsInWindow = buckets.reduce((sum, value) => sum + value, 0);
  const peakPerMinute = buckets.reduce((max, value) => Math.max(max, value), 0);
  const current = buckets[buckets.length - 1] || 0;
  const previous = buckets.length > 1 ? buckets[buckets.length - 2] : 0;
  const deltaPercent = previous > 0 ? ((current - previous) / previous) * 100 : null;

  return {
    routeCount: routes.length,
    enabledCount,
    disabledCount: routes.length - enabledCount,
    requestsLastMinute,
    requestsInWindow,
    peakPerMinute,
    averageDurationMs: totalRequests > 0 ? Math.round(totalDurationMs / totalRequests) : 0,
    trafficBuckets: buckets,
    deltaPercent,
  };
}

/** 概览带：展开时是「左侧白色主数字卡 + 右侧 30 分钟柱状带」；收起时压成一行摘要。 */
function OverviewBand({ totals, statusFilter, onStatusChange, mode, onToggleMode }: {
  totals: Totals;
  statusFilter: RouteStatusFilter;
  onStatusChange: (value: RouteStatusFilter) => void;
  mode: BandMode;
  onToggleMode: () => void;
}) {
  const [hovered, setHovered] = React.useState<number | null>(null);
  const peak = totals.peakPerMinute > 0 ? totals.peakPerMinute : 1;
  const delta = totals.deltaPercent;
  const deltaUp = delta !== null && delta >= 0;
  const collapsed = mode === 'collapsed';

  const toggle = (
    <button
      type="button"
      className="band-toggle"
      onClick={onToggleMode}
      aria-expanded={!collapsed}
      aria-controls="overview-band-body"
      aria-label={bandToggleLabel(mode)}
      title={bandToggleLabel(mode)}
    >
      {collapsed ? <ChevronDown className="h-4 w-4" aria-hidden="true" /> : <ChevronUp className="h-4 w-4" aria-hidden="true" />}
    </button>
  );

  if (collapsed) {
    return (
      <section className="band band-collapsed" aria-label="路由概览">
        <div className="band-summary">
          <span className="band-summary-icon" aria-hidden="true"><Gauge className="h-3.5 w-3.5" /></span>
          <span className="band-summary-figure">{formatCount(totals.requestsLastMinute)}</span>
          <span className="band-summary-unit">次 / 分钟</span>
          <span className="band-summary-sep" aria-hidden="true" />
          <span className="band-summary-text">
            30 分钟 {formatCount(totals.requestsInWindow)} 次 · 峰值 {formatCompactNumber(totals.peakPerMinute)} / 分钟 · 平均延迟 {formatLatency(totals.averageDurationMs)}
          </span>
          <span className="band-summary-stats">
            <button type="button" className="band-summary-stat" aria-pressed={statusFilter === 'all'} onClick={() => onStatusChange('all')}>
              <span className="dot" aria-hidden="true" />路由 {formatCount(totals.routeCount)}
            </button>
            <button type="button" className="band-summary-stat" aria-pressed={statusFilter === 'enabled'} onClick={() => onStatusChange('enabled')}>
              <span className="dot state-running" aria-hidden="true" />运行 {formatCount(totals.enabledCount)}
            </button>
            <button type="button" className="band-summary-stat" aria-pressed={statusFilter === 'disabled'} onClick={() => onStatusChange('disabled')}>
              <span className="dot state-stopped" aria-hidden="true" />停用 {formatCount(totals.disabledCount)}
            </button>
          </span>
          {toggle}
        </div>
      </section>
    );
  }

  return (
    <section className="band" aria-label="路由概览">
      <div className="band-lead">
        <div className="band-lead-head">
          <span className="band-lead-icon" aria-hidden="true"><Gauge className="h-3.5 w-3.5" /></span>
          <span className="band-lead-title">本分钟请求</span>
          <span className="chip">每 5 秒刷新</span>
          {toggle}
        </div>
        <div id="overview-band-body" className="band-lead-figure-row">
          <strong className="band-lead-figure">{formatCount(totals.requestsLastMinute)}</strong>
          <div className="band-lead-delta">
            {delta === null ? (
              <span>上一分钟没有请求，暂无可比数据</span>
            ) : (
              <>
                <span className="band-delta-chip">
                  {deltaUp ? <ArrowUpRight className="h-3 w-3" aria-hidden="true" /> : <ArrowDownRight className="h-3 w-3" aria-hidden="true" />}
                  {Math.abs(delta).toFixed(1)}%
                </span>
                <span>相比上一分钟</span>
              </>
            )}
          </div>
        </div>

        <div className="band-lead-split">
          <SplitStat label="路由总数" value={formatCount(totals.routeCount)} active={statusFilter === 'all'} onClick={() => onStatusChange('all')} icon={<RouteIcon className="h-3 w-3" />} />
          <SplitStat label="运行中" value={formatCount(totals.enabledCount)} tone="var(--surface-success)" active={statusFilter === 'enabled'} onClick={() => onStatusChange('enabled')} icon={<Activity className="h-3 w-3" />} />
          <SplitStat label="已停用" value={formatCount(totals.disabledCount)} tone="var(--surface-ink-subtle)" active={statusFilter === 'disabled'} onClick={() => onStatusChange('disabled')} icon={<PowerOff className="h-3 w-3" />} />
        </div>
      </div>

      <div className="band-chart">
        <div className="band-chart-head">
          <span className="band-chart-label">最近 30 分钟请求</span>
          <strong className="band-chart-figure">{formatCount(totals.requestsInWindow)}</strong>
          <span className="band-chart-note">
            峰值 {formatCompactNumber(totals.peakPerMinute)} / 分钟 · 平均延迟 {formatLatency(totals.averageDurationMs)}
          </span>
        </div>

        <div
          className={'band-bars' + (totals.requestsInWindow === 0 ? ' band-bars-empty' : '')}
          role="img"
          aria-label={'最近 30 分钟每分钟请求数，峰值 ' + totals.peakPerMinute + ' 次'}
        >
          {totals.trafficBuckets.map((value, index) => {
            const isLast = index === totals.trafficBuckets.length - 1;
            const isActive = hovered === index || (hovered === null && isLast);
            const height = value > 0 ? Math.max(6, Math.round((value / peak) * 100)) : 4;
            return (
              <span
                key={index}
                className={'band-bar' + (isActive ? ' band-bar-hot' : '')}
                style={{ height: height + '%' }}
                title={(totals.trafficBuckets.length - 1 - index) + ' 分钟前：' + value + ' 次'}
                onMouseEnter={() => setHovered(index)}
                onMouseLeave={() => setHovered(null)}
              />
            );
          })}
        </div>
        <div className="band-bar-ticks" aria-hidden="true">
          <span className="band-bar-tick">-29m</span>
          <span className="band-bar-tick">-15m</span>
          <span className="band-bar-tick">现在</span>
        </div>
      </div>
    </section>
  );
}

function SplitStat({ label, value, icon, tone, active = false, onClick }: { label: string; value: string; icon: React.ReactNode; tone?: string; active?: boolean; onClick: () => void }) {
  return (
    <button type="button" className="band-split-item" aria-pressed={active} onClick={onClick}>
      <span className="band-split-label">
        <span className="dot" style={tone ? { background: tone } : undefined} aria-hidden="true" />
        {label}
      </span>
      <span className="band-split-value">
        <span className="band-split-figure">{value}</span>
        <span className="band-split-unit">条</span>
      </span>
    </button>
  );
}

function AppTopbar({ gatewayState, version, theme, search, onSearchChange, onToggleTheme, onOpenVersion }: {
  gatewayState: GatewayState;
  version: string;
  theme: ThemeMode;
  search: string;
  onSearchChange: (value: string) => void;
  onToggleTheme: () => void;
  onOpenVersion: () => void;
}) {
  const running = gatewayState === 'running';
  const stateText = running ? 'Gateway 运行中' : gatewayState === 'checking' ? '正在检查' : 'Gateway 未响应';
  return (
    <header className="app-topbar">
      <div className="topbar-heading">
        <h1 className="topbar-title">路由控制台</h1>
        <p className="topbar-subtitle">管理本地路由、代理端口和请求转发</p>
      </div>
      <div className="topbar-actions">
        <label className="topbar-search">
          <span className="sr-only">搜索路由</span>
          <Search className="topbar-search-icon" aria-hidden="true" />
          <Input value={search} onChange={(event) => onSearchChange(event.target.value)} type="search" placeholder="搜索路由名称、Path、Target" />
          <span className="topbar-search-key">⌘K</span>
        </label>
        <span className="state-pill" title={running ? 'Gateway 运行中' : gatewayState === 'checking' ? '正在检查 Gateway 状态' : 'Gateway 未响应'}>
          <span className={'dot ' + (running ? 'state-running' : gatewayState === 'checking' ? 'state-checking' : 'state-error')} aria-hidden="true" />
          {stateText}
          <span className="state-pill-address">{GATEWAY_ENDPOINT}</span>
        </span>
        <button type="button" className="version-pill" onClick={onOpenVersion} aria-label={'当前版本 v' + version + '，查看版本与更新'} title="版本与更新">
          v{version}
        </button>
        <button
          type="button"
          className="icon-button"
          onClick={onToggleTheme}
          aria-label={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
          title={theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'}
        >
          {theme === 'dark' ? <Sun className="h-4 w-4" aria-hidden="true" /> : <Moon className="h-4 w-4" aria-hidden="true" />}
        </button>
        <button type="button" className="icon-button icon-button-dot" aria-label="通知"><Bell className="h-4 w-4" aria-hidden="true" /></button>
        <span className="topbar-avatar" aria-hidden="true">WR</span>
      </div>
    </header>
  );
}

interface SidebarProps {
  mode: SidebarMode;
  onToggleMode: () => void;
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

function AppSidebar({ mode, onToggleMode, routeCount, gatewayState, view, onViewChange, configPathFull, showConfigPath, onToggleConfigPath, onExport, onImport, exporting, importing, version, platform, onOpenVersion }: SidebarProps) {
  const importRef = React.useRef<HTMLInputElement>(null);
  const running = gatewayState === 'running';
  const collapsed = mode === 'collapsed';
  // 收起态只留图标：所有可点元素都要有 title / aria-label 兜底
  const tip = (label: string) => (collapsed ? label : undefined);

  return (
    <aside className="app-sidebar scroll-area">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true"><RouteNodeMark /></span>
        <span className="brand-text">
          <span className="brand-name">wrouter</span>
          <span className="brand-sub">Gateway Console</span>
        </span>
      </div>

      <nav className="side-section" aria-label="主导航">
        <button type="button" className={'nav-item' + (view === 'routes' ? ' nav-item-active' : '')} onClick={() => onViewChange('routes')} title={tip('路由管理')}>
          <LayoutGrid className="nav-icon" aria-hidden="true" />
          <span className="nav-text">路由管理</span>
          <span className="nav-count">{routeCount}</span>
        </button>
        <button type="button" className={'nav-item' + (view === 'logs' ? ' nav-item-active' : '')} onClick={() => onViewChange('logs')} title={tip('请求日志')}>
          <ScrollText className="nav-icon" aria-hidden="true" />
          <span className="nav-text">请求日志</span>
        </button>
      </nav>

      <div className="side-section side-section-status">
        <p className="side-label">运行状态</p>
        <div className="nav-item" style={{ cursor: 'default' }}>
          <span className={'dot ' + (running ? 'state-running' : 'state-error')} aria-hidden="true" />
          <span className="nav-text">Gateway</span>
        </div>
        <div className="nav-item" style={{ cursor: 'default' }}>
          <span className={'dot ' + (running ? 'state-running' : 'state-stopped')} aria-hidden="true" />
          <span className="nav-text">Local Proxy</span>
        </div>
      </div>

      <div className="side-section">
        <p className="side-label">系统设置</p>
        <button type="button" className={'nav-item' + (showConfigPath ? ' nav-item-active' : '')} onClick={onToggleConfigPath} aria-expanded={showConfigPath} title={tip('配置目录')}>
          <Settings2 className="nav-icon" aria-hidden="true" />
          <span className="nav-text">配置目录</span>
        </button>
        {showConfigPath && (
          <span className="status-panel-paths">{configPathFull || '配置目录已隐藏'}</span>
        )}
        <button type="button" className="nav-item" onClick={onExport} disabled={exporting} title={tip('导出配置')}>
          <Download className="nav-icon" aria-hidden="true" />
          <span className="nav-text">{exporting ? '导出中…' : '导出配置'}</span>
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
        <button type="button" className="nav-item" onClick={() => importRef.current?.click()} disabled={importing} title={tip('导入配置')}>
          <Upload className="nav-icon" aria-hidden="true" />
          <span className="nav-text">{importing ? '导入中…' : '导入配置'}</span>
        </button>
        <button type="button" className="nav-item" onClick={onOpenVersion} title={tip('关于')}>
          <CircleHelp className="nav-icon" aria-hidden="true" />
          <span className="nav-text">关于</span>
        </button>
      </div>

      <div className="side-spacer" />

      <div className="status-panel">
        <div className="status-panel-head">
          <span className={'dot ' + (running ? 'state-running' : gatewayState === 'checking' ? 'state-checking' : 'state-error')} aria-hidden="true" />
          <span className="status-panel-title">{running ? '网关运行中' : gatewayState === 'checking' ? '正在检查网关' : '网关未响应'}</span>
        </div>
        <span className="status-panel-address">{GATEWAY_ENDPOINT}</span>
        <button type="button" className="status-panel-meta" onClick={onOpenVersion} title={platformLabel(platform) + ' · 查看版本与更新'}>
          v{version} · {platformLabel(platform)}
        </button>
      </div>

      {/* 收起按钮固定在侧栏最下方：与运行卡同处底部区域，视线更集中 */}
      <div className="sidebar-footer">
        <button
          type="button"
          className="sidebar-toggle"
          onClick={onToggleMode}
          aria-expanded={!collapsed}
          aria-label={sidebarToggleLabel(mode)}
          title={sidebarToggleLabel(mode)}
        >
          {collapsed ? <PanelLeftOpen className="h-4 w-4" aria-hidden="true" /> : <PanelLeftClose className="h-4 w-4" aria-hidden="true" />}
          <span className="sidebar-toggle-label">{sidebarToggleLabel(mode)}</span>
        </button>
      </div>
    </aside>
  );
}

function SkeletonCards() {
  return (
    <>
      {[0, 1, 2, 3, 4, 5].map((index) => (
        <div key={index} className="skeleton h-[196px]" aria-hidden="true" />
      ))}
      <span className="sr-only">正在加载路由…</span>
    </>
  );
}

/** 品牌符号：节点与连线的极简写法，用当前色描边，随主题切换。 */
function RouteNodeMark() {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true">
      <path d="M6 7h12" />
      <path d="M6 7l6 10" />
      <path d="M18 7l-6 10" />
      <circle cx="6" cy="7" r="2.2" fill="var(--surface-navy)" />
      <circle cx="18" cy="7" r="2.2" fill="var(--surface-navy)" />
      <circle cx="12" cy="17" r="2.2" fill="var(--surface-navy)" />
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
  return 'wrouter-routes-' + stamp + '.json';
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