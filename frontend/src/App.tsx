import * as React from 'react';
import { Activity, ArrowRight, Eye, EyeOff, PowerOff, Route as RouteIcon, Sparkles } from 'lucide-react';
import { Toaster, toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { fetchJson, jsonRequest } from '@/lib/api';
import { stripProtocol } from '@/lib/utils';
import type { RouteConfig, RouteConfigPayload } from '@/features/routes/types';
import { activeLocalBinding, displayTargetUrl, effectivePathPrefixes, localBinding, routeAccessUrl } from '@/features/routes/route-utils';
import { formatJsonContent } from '@/features/routes/route-detail-utils';
import { RouteToolbar } from '@/features/routes/RouteToolbar';
import { RouteCard } from '@/features/routes/RouteCard';
import { RouteFormDialog } from '@/features/routes/RouteFormDialog';
import { RouteDetailDrawer as RouteDetailPage } from '@/features/routes/RouteDetailDrawer';
import { DeleteConfirmDialog } from '@/features/routes/DeleteConfirmDialog';
import './styles.css';

interface RawConfigResponse {
  fileName: string;
  content: string;
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

type RouteFilter = 'enabled' | 'disabled' | 'all';

export default function App() {
  const [routes, setRoutes] = React.useState<RouteConfig[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [search, setSearch] = React.useState('');
  const [routeFilter, setRouteFilter] = React.useState<RouteFilter>('enabled');
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [showConfigPath, setShowConfigPath] = React.useState(false);
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

  React.useEffect(() => {
    void loadRoutes();
  }, [loadRoutes]);

  const routeCounts = React.useMemo(() => {
    const enabled = routes.filter((route) => route.enabled).length;
    return { all: routes.length, enabled, disabled: routes.length - enabled };
  }, [routes]);

  const filteredRoutes = React.useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return routes
      .filter((route) => {
        if (routeFilter === 'enabled') {
          return route.enabled;
        }
        if (routeFilter === 'disabled') {
          return !route.enabled;
        }
        return true;
      })
      .filter((route) => !keyword || [route.name, displayTargetUrl(route.targetUrl), displayTargetUrl(route.accessPageBaseUrl), localBinding(route.localIp, route.localPort), ...effectivePathPrefixes(route)].some((value) => value.toLowerCase().includes(keyword)));
  }, [routes, routeFilter, search]);

  const existingNames = React.useMemo(() => routes.map((route) => route.name), [routes]);
  const existingBindings = React.useMemo(() => routes.map(activeLocalBinding).filter(Boolean), [routes]);
  const selectedRoutes = React.useMemo(() => routes.filter((route) => selectedIds.includes(route.id)), [routes, selectedIds]);

  async function handleSaveRoute(payload: RouteConfigPayload, mode: 'create' | 'edit' | 'copy', route?: RouteConfig | null) {
    const url = mode === 'edit' && route ? '/admin/api/routes/' + encodeURIComponent(route.id) : '/admin/api/routes';
    const method = mode === 'edit' ? 'PUT' : 'POST';
    await fetchJson<RouteConfig>(url, jsonRequest(payload, method));
    toast.success(mode === 'edit' ? '路由已更新' : '路由已创建');
    await loadRoutes();
  }

  async function openDetail(route: RouteConfig) {
    setDetailDrawer({ open: true, route, fileName: `${route.id}.json`, content: '', loading: true, error: '' });
    try {
      const raw = await fetchJson<RawConfigResponse>('/admin/api/routes/' + encodeURIComponent(route.id) + '/raw');
      setDetailDrawer({ open: true, route, fileName: raw.fileName, content: formatJsonContent(raw.content), loading: false, error: '' });
    } catch (error) {
      setDetailDrawer({ open: true, route, fileName: `${route.id}.json`, content: '', loading: false, error: error instanceof Error ? error.message : '读取配置失败' });
      toast.error(error instanceof Error ? error.message : '读取配置失败');
    }
  }

  async function reloadDetail(route: RouteConfig) {
    try {
      const raw = await fetchJson<RawConfigResponse>('/admin/api/routes/' + encodeURIComponent(route.id) + '/raw');
      setDetailDrawer({ open: true, route, fileName: raw.fileName, content: formatJsonContent(raw.content), loading: false, error: '' });
    } catch (error) {
      setDetailDrawer({ open: true, route, fileName: `${route.id}.json`, content: '', loading: false, error: error instanceof Error ? error.message : '读取配置失败' });
    }
  }

  async function saveDetailRoute(payload: RouteConfigPayload, mode: 'edit', route: RouteConfig) {
    const updated = await fetchJson<RouteConfig>('/admin/api/routes/' + encodeURIComponent(route.id), jsonRequest(payload, 'PUT'));
    toast.success('路由已更新');
    await loadRoutes();
    await reloadDetail(updated);
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
      await loadRoutes();
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

  return (
    <main className="min-h-screen px-3 py-4 text-clay-ink sm:px-4 sm:py-5 lg:px-6">
      <div className="mx-auto grid max-w-[1536px] gap-5">
        {detailDrawer.open && detailDrawer.route ? (
          <RouteDetailPage
            open={detailDrawer.open}
            route={detailDrawer.route}
            fileName={detailDrawer.fileName}
            content={detailDrawer.content}
            loading={detailDrawer.loading}
            error={detailDrawer.error}
            onOpenChange={(open) => setDetailDrawer((current) => ({ ...current, open }))}
            onSaveRoute={saveDetailRoute}
            existingNames={existingNames}
            existingBindings={existingBindings}
            onAccess={openAccess}
            onDelete={(route) => setDeleteIds([route.id])}
          />
        ) : (
          <>
        <ChunkyHero />
        <section className="overview-grid grid gap-4" aria-label="路由概览">
          <StatusCard tone="chunky-card-yellow" label="配置总数" value={routeCounts.all} icon={<RouteIcon className="h-6 w-6" />} active={routeFilter === 'all'} ariaLabel="显示所有路由" onClick={() => setRouteFilter('all')} />
          <StatusCard tone="chunky-card-mint" label="启用路由" value={routeCounts.enabled} icon={<Activity className="h-6 w-6" />} active={routeFilter === 'enabled'} ariaLabel="过滤启用路由" onClick={() => setRouteFilter('enabled')} />
          <StatusCard tone="chunky-card-pink" label="停用路由" value={routeCounts.disabled} icon={<PowerOff className="h-6 w-6" />} active={routeFilter === 'disabled'} ariaLabel="过滤停用路由" onClick={() => setRouteFilter('disabled')} />
          <Card className="chunky-card-blue overview-stat-card overview-config-card overflow-hidden p-5" data-config-path-visible={showConfigPath}>
            <div className="config-path-card min-w-0">
              <div className="min-w-0">
                <span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">配置目录</span>
              </div>
              <Button className="overview-stat-icon overview-config-toggle shrink-0" size="icon" variant="outline" aria-label={showConfigPath ? '隐藏配置目录绝对路径' : '显示配置目录绝对路径'} aria-expanded={showConfigPath} onClick={() => setShowConfigPath((value) => !value)}>
                {showConfigPath ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </Button>
              <div className="config-path-value-slot">
                {showConfigPath ? (
                  <strong className="config-path-value block text-xl font-black" title={configPathFull}>{configPathFull || '未获取到配置目录绝对路径'}</strong>
                ) : (
                  <span className="config-path-value block text-sm font-black text-clay-muted">配置目录已隐藏</span>
                )}
              </div>
            </div>
          </Card>
        </section>

        <section className="route-workspace chunky-panel bg-white p-4 sm:p-5" aria-labelledby="route-config-heading">
          <RouteToolbar headingId="route-config-heading" search={search} selectedCount={selectedIds.length} onSearchChange={setSearch} onAdd={() => setForm({ open: true, mode: 'create', route: null })} onBatchDelete={() => setDeleteIds(selectedIds)} />

          <div className="route-card-board mt-4 grid gap-4" aria-live="polite">
            {loading ? (
              <Card className="glass-card-purple col-span-full p-10 text-center text-xl font-black">正在加载路由...</Card>
            ) : filteredRoutes.length === 0 ? (
              <Card className="glass-card-gold col-span-full p-10 text-center">
                <strong className="block text-2xl font-black">还没有接上线的路由</strong>
                <span className="mt-2 block font-bold text-clay-muted">点击「新增路由」配置默认地址、监听端口和访问页，按需添加路径前缀。</span>
              </Card>
            ) : filteredRoutes.map((route, index) => (
              <RouteCard
                key={route.id}
                route={route}
                index={index}
                selected={selectedIds.includes(route.id)}
                onSelectedChange={(selected) => setSelectedIds((current) => selected ? Array.from(new Set([...current, route.id])) : current.filter((id) => id !== route.id))}
                onView={() => void openDetail(route)}
                onCopy={() => setForm({ open: true, mode: 'copy', route })}
                onAccess={() => openAccess(route)}
                onToggle={() => void toggleRoute(route)}
                onDelete={() => setDeleteIds([route.id])}
              />
            ))}
          </div>
        </section>
          </>
        )}
      </div>

      <RouteFormDialog open={form.open} mode={form.mode} route={form.route} existingNames={existingNames} existingBindings={existingBindings} onOpenChange={(open) => setForm((current) => ({ ...current, open }))} onSubmit={handleSaveRoute} />
      <DeleteConfirmDialog open={deleteIds.length > 0} names={routes.filter((route) => deleteIds.includes(route.id)).map((route) => route.name)} onOpenChange={(open) => !open && setDeleteIds([])} onConfirm={() => void deleteRoutes(deleteIds)} />
      <Toaster richColors position="top-right" />
    </main>
  );
}

function ChunkyHero() {
  return (
    <header className="chunky-panel relative overflow-hidden bg-white p-5 sm:p-6 lg:p-8">
      <div className="absolute -right-10 -top-10 h-28 w-28 rounded-full border-[3px] border-clay-border bg-clay-yellow" />
      <div className="relative grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(340px,430px)] lg:items-center">
        <div>
          <Badge variant="orange" className="mb-4 gap-2"><Sparkles className="h-4 w-4" />WEB ROUTER CONTROL</Badge>
          <h1 className="hero-title max-w-3xl text-4xl font-black leading-none text-clay-ink md:text-6xl">路由管理</h1>
          <p className="hero-copy mt-3 max-w-2xl text-lg font-extrabold text-clay-muted">路径转发、监听端口、实时日志，一处管理。</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Badge variant="yellow">JSON 配置</Badge>
            <Badge variant="mint">实时日志</Badge>
          </div>
        </div>
        <div className="flow-steps text-center font-black" aria-label="请求转发流程">
          <FlowStep label="请求" value="/api/**" tone="chunky-card-blue" />
          <FlowArrow />
          <FlowStep label="匹配" value="前缀" tone="chunky-card-yellow" />
          <FlowArrow />
          <FlowStep label="转发" value="host:port" tone="chunky-card-mint" />
        </div>
      </div>
    </header>
  );
}

function FlowArrow() {
  return <span className="flow-arrow" aria-hidden="true"><ArrowRight className="h-4 w-4" /></span>;
}

function FlowStep({ label, value, tone }: { label: string; value: string; tone: string }) {
  return <div className={`flow-step ${tone} chunky-pressable rounded-[24px] border-[3px] border-clay-border p-3 shadow-clay-sm`}><strong className="block text-sm md:text-base">{value}</strong><small className="font-black text-clay-muted">{label}</small></div>;
}

function StatusCard({ label, value, icon, tone, active = false, ariaLabel, onClick }: { label: string; value: number; icon: React.ReactNode; tone: string; active?: boolean; ariaLabel?: string; onClick?: () => void }) {
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
    <Card className={`${tone} overview-stat-card p-5${active ? ' overview-stat-card-active' : ''}`} role={onClick ? 'button' : undefined} tabIndex={onClick ? 0 : undefined} aria-label={active && ariaLabel ? `${ariaLabel}，当前筛选条件` : ariaLabel} aria-pressed={onClick ? active : undefined} onClick={onClick} onKeyDown={handleKeyDown}>
      <div className="flex items-center justify-between gap-4">
        <div>
          <div className="overview-stat-label-row">
            <span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">{label}</span>
            {active && <span className="overview-filter-badge">当前筛选</span>}
          </div>
          <strong className="overview-stat-value mt-2 block text-4xl font-black text-clay-ink">{value}</strong>
        </div>
        <div className="overview-stat-icon rounded-2xl border-[3px] border-clay-border bg-white p-3 shadow-clay-sm">{icon}</div>
      </div>
    </Card>
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
