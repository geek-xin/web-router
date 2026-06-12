import * as React from 'react';
import { Copy, Edit3, ExternalLink, FileJson, Power, ScrollText, Trash2, X } from 'lucide-react';
import { toast } from 'sonner';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { copyText } from '@/lib/utils';
import type { RouteConfig } from './types';
import { activeLocalBinding, displayTargetUrl, effectivePathPrefixes, routeAccessUrl } from './route-utils';
import { routeDetailMetrics, routeStatusLabel, routeStatusTone } from './route-detail-utils';

interface RouteDetailDrawerProps {
  open: boolean;
  route: RouteConfig | null;
  fileName: string;
  content: string;
  loading: boolean;
  error: string;
  onOpenChange: (open: boolean) => void;
  onSaveJson: (content: string) => Promise<void>;
  onEdit: (route: RouteConfig) => void;
  onLogs: (route: RouteConfig) => void;
  onAccess: (route: RouteConfig) => void;
  onDelete: (route: RouteConfig) => void;
}

const metricToneClass = {
  mint: 'bg-clay-mint',
  blue: 'bg-clay-cyan',
  yellow: 'bg-clay-yellow',
  pink: 'bg-clay-pink',
};

export function RouteDetailDrawer({ open, route, fileName, content, loading, error, onOpenChange, onSaveJson, onEdit, onLogs, onAccess, onDelete }: RouteDetailDrawerProps) {
  const [value, setValue] = React.useState(content);
  const [editing, setEditing] = React.useState(false);
  const [jsonError, setJsonError] = React.useState('');
  const [saving, setSaving] = React.useState(false);
  const closeButtonRef = React.useRef<HTMLButtonElement | null>(null);

  React.useEffect(() => {
    setValue(content);
    setEditing(false);
    setJsonError('');
  }, [content, route?.id, open]);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    closeButtonRef.current?.focus();
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onOpenChange(false);
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [onOpenChange, open]);

  if (!open || !route) {
    return null;
  }

  const prefixes = effectivePathPrefixes(route);
  const metrics = routeDetailMetrics(route);
  const statusTone = routeStatusTone(route);
  const accessUrl = routeAccessUrl({ localBinding: activeLocalBinding(route), accessPage: route.accessPage, pathPrefixes: prefixes });

  async function handleCopyAccess() {
    if (!accessUrl) {
      return;
    }
    await copyText(accessUrl);
    toast.success('访问地址已复制');
  }

  async function handleSave() {
    try {
      JSON.parse(value);
    } catch (parseError) {
      setJsonError('JSON 格式不正确，请检查后再保存');
      return;
    }
    setSaving(true);
    try {
      await onSaveJson(value);
      setEditing(false);
      setJsonError('');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 bg-black/45" role="presentation">
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="route-detail-title"
        className="fixed right-0 top-0 flex h-full w-full max-w-[620px] flex-col overflow-auto border-l-[3px] border-clay-border bg-[#F8F6F3] p-5 shadow-[-10px_0_0_#111111] sm:p-6"
      >
        <div className="mb-5 flex items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-3">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border-[3px] border-clay-border bg-clay-primary text-2xl font-black text-white shadow-clay-sm">
              {route.name.slice(0, 1).toUpperCase()}
            </div>
            <div className="min-w-0">
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <Badge variant={statusTone === 'enabled' ? 'mint' : 'muted'} className="gap-1">
                  <Power className="h-3 w-3" />
                  {routeStatusLabel(route)}
                </Badge>
                <Badge variant="yellow" className="gap-1">
                  <FileJson className="h-3 w-3" />
                  {fileName || `${route.id}.json`}
                </Badge>
              </div>
              <h2 id="route-detail-title" className="truncate text-3xl font-black text-clay-ink" title={route.name}>{route.name}</h2>
              <p className="mt-1 truncate text-sm font-bold text-clay-muted" title={route.id}>{route.id}</p>
            </div>
          </div>
          <Button ref={closeButtonRef} size="icon" variant="outline" aria-label="关闭路由详情" onClick={() => onOpenChange(false)}>
            <X className="h-5 w-5" />
          </Button>
        </div>

        {error && <div className="mb-4 rounded-2xl border-[3px] border-clay-border bg-[#FFD9D3] p-3 text-sm font-black text-clay-ink">{error}</div>}

        <section className="grid gap-3 sm:grid-cols-2" aria-label="路由关键数据">
          {metrics.map((metric) => (
            <div key={metric.label} className={`${metricToneClass[metric.tone]} rounded-2xl border-[3px] border-clay-border p-4 shadow-clay-sm`}>
              <span className="text-xs font-black uppercase tracking-wide text-clay-muted">{metric.label}</span>
              <strong className="mt-1 block truncate text-xl font-black text-clay-ink" title={metric.value}>{metric.value}</strong>
            </div>
          ))}
        </section>

        <section className="mt-5 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
          <h3 className="mb-3 text-lg font-black text-clay-ink">路径前缀</h3>
          <div className="flex flex-wrap gap-2">
            {prefixes.length === 0 ? <Badge variant="yellow">未配置路径前缀，请求走默认地址</Badge> : prefixes.map((prefix) => <Badge key={prefix} variant="indigo">{prefix}</Badge>)}
          </div>
        </section>

        <section className="mt-5 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-lg font-black text-clay-ink">原始 JSON</h3>
              <p className="text-sm font-bold text-clay-muted">编辑后会通过现有路由 API 保存并刷新代理。</p>
            </div>
            {editing ? (
              <Button size="sm" variant="orange" onClick={() => void handleSave()} disabled={saving || loading}>{saving ? '保存中...' : '保存 JSON'}</Button>
            ) : (
              <Button size="sm" variant="primary" onClick={() => setEditing(true)} disabled={loading}>编辑此文件</Button>
            )}
          </div>
          {jsonError && <div className="mb-3 rounded-2xl border-[3px] border-clay-border bg-[#FFD9D3] p-3 text-sm font-black text-clay-ink">{jsonError}</div>}
          <Textarea value={loading ? '正在加载配置...' : value} onChange={(event) => setValue(event.target.value)} readOnly={!editing || loading} rows={14} />
        </section>

        <section className="mt-5 rounded-[24px] border-[3px] border-clay-border bg-clay-cream p-4 shadow-clay-sm">
          <h3 className="mb-3 text-lg font-black text-clay-ink">快捷操作</h3>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={() => void handleCopyAccess()} disabled={!accessUrl}><Copy className="h-4 w-4" />复制访问地址</Button>
            <Button size="sm" variant="primary" onClick={() => onAccess(route)} disabled={!accessUrl}><ExternalLink className="h-4 w-4" />打开访问页</Button>
            <Button size="sm" variant="outline" onClick={() => onEdit(route)}><Edit3 className="h-4 w-4" />编辑</Button>
            <Button size="sm" variant="outline" onClick={() => onLogs(route)}><ScrollText className="h-4 w-4" />查看日志</Button>
            <Button size="sm" variant="danger" onClick={() => onDelete(route)}><Trash2 className="h-4 w-4" />删除</Button>
          </div>
        </section>

        <section className="mt-5 rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
          <h3 className="mb-2 text-lg font-black text-clay-ink">访问摘要</h3>
          <dl className="grid gap-2 text-sm font-bold text-clay-muted">
            <div className="flex justify-between gap-4"><dt>默认目标</dt><dd className="truncate text-clay-ink" title={route.targetUrl}>{displayTargetUrl(route.targetUrl)}</dd></div>
            <div className="flex justify-between gap-4"><dt>代理地址</dt><dd className="truncate text-clay-ink" title={route.accessPageBaseUrl || ''}>{displayTargetUrl(route.accessPageBaseUrl) || '未配置'}</dd></div>
            <div className="flex justify-between gap-4"><dt>访问页</dt><dd className="truncate text-clay-ink" title={route.accessPage || ''}>{route.accessPage || '使用第一个路径前缀'}</dd></div>
          </dl>
        </section>
      </aside>
    </div>
  );
}
