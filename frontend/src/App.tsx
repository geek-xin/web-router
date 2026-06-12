import * as React from 'react';
import { Activity, Eye, Network, RefreshCw, Route as RouteIcon, Sparkles } from 'lucide-react';
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
import { RouteDetailDrawer } from '@/features/routes/RouteDetailDrawer';
import { DeleteConfirmDialog } from '@/features/routes/DeleteConfirmDialog';
import { RouteLogDialog } from '@/features/logs/RouteLogDialog';
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

export default function App() {
  const [routes, setRoutes] = React.useState<RouteConfig[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [search, setSearch] = React.useState('');
  const [selectedIds, setSelectedIds] = React.useState<string[]>([]);
  const [showConfigPath, setShowConfigPath] = React.useState(false);
  const [form, setForm] = React.useState<FormState>({ open: false, mode: 'create', route: null });
  const [detailDrawer, setDetailDrawer] = React.useState<DetailDrawerState>({ open: false, route: null, fileName: '', content: '', loading: false, error: '' });
  const [deleteIds, setDeleteIds] = React.useState<string[]>([]);
  const [logRoute, setLogRoute] = React.useState<RouteConfig | null>(null);

  const configMeta = React.useMemo(() => readConfigMeta(), []);
  const configPathLabel = configMeta.label;
  const configPathFull = configMeta.full;

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

  const filteredRoutes = React.useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return routes;
    }
    return routes.filter((route) => [route.name, displayTargetUrl(route.targetUrl), displayTargetUrl(route.accessPageBaseUrl), localBinding(route.localIp, route.localPort), ...effectivePathPrefixes(route)].some((value) => value.toLowerCase().includes(keyword)));
  }, [routes, search]);

  const existingNames = React.useMemo(() => routes.map((route) => route.name), [routes]);
  const existingBindings = React.useMemo(() => routes.map((route) => localBinding(route.localIp, route.localPort)).filter(Boolean), [routes]);
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

  async function saveDetailJson(content: string) {
    if (!detailDrawer.route) {
      return;
    }
    const parsed = JSON.parse(content) as RouteConfigPayload;
    const updated = await fetchJson<RouteConfig>('/admin/api/routes/' + encodeURIComponent(detailDrawer.route.id), jsonRequest(parsed, 'PUT'));
    toast.success('JSON 配置已保存');
    await loadRoutes();
    setDetailDrawer((current) => ({ ...current, route: updated, content: formatJsonContent(content), open: true, loading: false, error: '' }));
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
    <main className="min-h-screen px-4 py-6 text-clay-ink sm:px-6 lg:px-8">
      <div className="mx-auto grid max-w-7xl gap-6">
        <BankingHero onRefresh={loadRoutes} loading={loading} />
        <section className="grid gap-4 md:grid-cols-3" aria-label="路由概览">
          <StatusCard tone="glass-card-gold" label="配置总数" value={routes.length} icon={<RouteIcon className="h-6 w-6" />} />
          <StatusCard tone="glass-card-green" label="启用路由" value={routes.filter((route) => route.enabled).length} icon={<Activity className="h-6 w-6" />} />
          <Card className="glass-card-cyan p-5">
            <div className="flex items-center justify-between gap-3">
              <div>
                <span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">配置目录</span>
                <strong className="mt-2 block truncate text-2xl font-black" title={configPathFull}>{showConfigPath ? configPathFull : configPathLabel}</strong>
              </div>
              <Button size="icon" variant="outline" aria-label={showConfigPath ? '隐藏完整配置目录' : '显示完整配置目录'} aria-expanded={showConfigPath} onClick={() => setShowConfigPath((value) => !value)}>
                <Eye className="h-5 w-5" />
              </Button>
            </div>
          </Card>
        </section>

        <RouteToolbar search={search} selectedCount={selectedIds.length} onSearchChange={setSearch} onAdd={() => setForm({ open: true, mode: 'create', route: null })} onBatchDelete={() => setDeleteIds(selectedIds)} />

        <section className="grid gap-5 lg:grid-cols-2" aria-live="polite">
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
              onLogs={() => setLogRoute(route)}
              onCopy={() => setForm({ open: true, mode: 'copy', route })}
              onEdit={() => setForm({ open: true, mode: 'edit', route })}
              onAccess={() => openAccess(route)}
              onToggle={() => void toggleRoute(route)}
              onDelete={() => setDeleteIds([route.id])}
            />
          ))}
        </section>
      </div>

      <RouteFormDialog open={form.open} mode={form.mode} route={form.route} existingNames={existingNames} existingBindings={existingBindings} onOpenChange={(open) => setForm((current) => ({ ...current, open }))} onSubmit={handleSaveRoute} />
      <RouteDetailDrawer
        open={detailDrawer.open}
        route={detailDrawer.route}
        fileName={detailDrawer.fileName}
        content={detailDrawer.content}
        loading={detailDrawer.loading}
        error={detailDrawer.error}
        onOpenChange={(open) => setDetailDrawer((current) => ({ ...current, open }))}
        onSaveJson={saveDetailJson}
        onEdit={(route) => setForm({ open: true, mode: 'edit', route })}
        onLogs={(route) => setLogRoute(route)}
        onAccess={openAccess}
        onDelete={(route) => setDeleteIds([route.id])}
      />
      <DeleteConfirmDialog open={deleteIds.length > 0} names={routes.filter((route) => deleteIds.includes(route.id)).map((route) => route.name)} onOpenChange={(open) => !open && setDeleteIds([])} onConfirm={() => void deleteRoutes(deleteIds)} />
      <RouteLogDialog open={Boolean(logRoute)} route={logRoute} onOpenChange={(open) => !open && setLogRoute(null)} />
      <Toaster richColors position="top-right" />
    </main>
  );
}

function BankingHero({ onRefresh, loading }: { onRefresh: () => void; loading: boolean }) {
  return (
    <header className="glass-panel relative overflow-hidden p-6 md:p-8">
      <div className="absolute -right-12 -top-12 h-40 w-40 rounded-full bg-clay-primary/35 blur-2xl" />
      <div className="absolute -left-16 bottom-0 h-36 w-36 rounded-full bg-clay-accent/30 blur-2xl" />
      <div className="absolute bottom-4 right-10 hidden rotate-6 rounded-[24px] border border-clay-border bg-clay-glass p-5 shadow-clay backdrop-blur-[18px] md:block">
        <Network className="h-14 w-14 text-clay-ink" />
      </div>
      <div className="relative grid gap-6 md:grid-cols-[1fr_360px] md:items-center">
        <div>
          <Badge variant="indigo" className="mb-4 gap-2"><Sparkles className="h-4 w-4" />WEB ROUTER TRUST CONSOLE</Badge>
          <h1 className="max-w-3xl text-4xl font-black leading-tight text-clay-ink md:text-6xl">路由管理</h1>
          <p className="mt-3 max-w-2xl text-lg font-bold text-clay-muted">以数字银行级玻璃态视图管理路径转发、监听端口和实时日志，重点突出安全、稳定和可信状态。</p>
          <div className="mt-5 flex flex-wrap gap-3">
            <Button variant="primary" onClick={onRefresh} disabled={loading}><RefreshCw className="h-4 w-4" />刷新路由</Button>
            <Badge variant="mint">SSE 实时日志</Badge>
            <Badge variant="yellow">玻璃态卡片</Badge>
          </div>
        </div>
        <div className="grid grid-cols-3 items-center gap-3 text-center font-black">
          <FlowStep label="请求" value="/api/**" tone="glass-card-blue" />
          <FlowStep label="匹配" value="前缀" tone="glass-card-gold" />
          <FlowStep label="转发" value="host:port" tone="glass-card-green" />
        </div>
      </div>
    </header>
  );
}

function FlowStep({ label, value, tone }: { label: string; value: string; tone: string }) {
  return <div className={`${tone} glass-pressable rounded-[24px] border border-clay-border p-4 shadow-clay-sm backdrop-blur-[16px]`}><strong className="block text-sm md:text-base">{value}</strong><small className="font-black text-clay-muted">{label}</small></div>;
}

function StatusCard({ label, value, icon, tone }: { label: string; value: number; icon: React.ReactNode; tone: string }) {
  return (
    <Card className={`${tone} p-5`}>
      <div className="flex items-center justify-between gap-4">
        <div>
          <span className="text-xs font-black uppercase tracking-[0.2em] text-clay-muted">{label}</span>
          <strong className="mt-2 block text-4xl font-black text-clay-ink">{value}</strong>
        </div>
        <div className="rounded-2xl border border-clay-border bg-white/10 p-3 shadow-clay-sm">{icon}</div>
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

function readConfigMeta(): { label: string; full: string } {
  const label = document.querySelector<HTMLMetaElement>('meta[name="routes-config-dir-label"]')?.content || 'config/routes';
  const full = document.querySelector<HTMLMetaElement>('meta[name="routes-config-dir"]')?.content || label;
  return { label, full };
}
