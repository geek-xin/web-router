import { Copy, Eye, FileJson, Globe2, MousePointer2, ScrollText, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import type { RouteConfig } from './types';
import { activeLocalBinding, displayTargetUrl, effectivePathPrefixes, hasLocalPort, localBinding, prefixToneClass, routeAccessUrl, routeCardToneClass } from './route-utils';
import { cn } from '@/lib/utils';

interface RouteCardProps {
  route: RouteConfig;
  index: number;
  selected: boolean;
  onSelectedChange: (selected: boolean) => void;
  onView: () => void;
  onCopy: () => void;
  onAccess: () => void;
  onToggle: () => void;
  onDelete: () => void;
}

export function RouteCard({ route, index, selected, onSelectedChange, onView, onCopy, onAccess, onToggle, onDelete }: RouteCardProps) {
  const prefixes = effectivePathPrefixes(route);
  const activeBinding = activeLocalBinding(route);
  const configuredBinding = localBinding(route.localIp, route.localPort);
  const accessUrl = routeAccessUrl({ localBinding: activeBinding, accessPage: route.accessPage, pathPrefixes: prefixes });
  const canAccess = Boolean(accessUrl);
  const canToggle = route.enabled || hasLocalPort(route);
  const theme = routeCardToneClass(route, index);

  return (
    <Card className={cn('group relative overflow-hidden p-0 transition-all duration-200 hover:-translate-y-1 hover:shadow-clay-hover', theme, selected && 'ring-4 ring-clay-primary/40')}>
      <div className="route-card-head flex min-h-[72px] items-center gap-3 border-b-[3px] border-clay-border px-4 py-3">
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2">
            <Checkbox className="h-5 w-5 rounded-lg bg-white shadow-none" checked={selected} onCheckedChange={(value) => onSelectedChange(value === true)} aria-label={`选择路由 ${route.name}`} />
            <h3 className="route-card-title-text min-w-0 truncate text-xl font-black text-clay-ink" title={route.name}>{route.name}</h3>
            <Badge className="shrink-0 px-2 py-0.5 text-[10px]" variant={route.enabled ? 'mint' : 'muted'}>{route.enabled ? '运行中' : '已停用'}</Badge>
          </div>
        </div>
        <Button className="route-toggle-button shrink-0" size="sm" variant={route.enabled ? 'danger' : 'primary'} onClick={onToggle} disabled={!canToggle} title={!canToggle ? '请先编辑路由并填写监听端口后再启用' : undefined}>
          {route.enabled ? '停用' : '启用'}
        </Button>
      </div>
      <div className="route-card-body relative p-4">

      <div className="relative grid gap-2.5">
        <Info label="监听地址" icon={<MousePointer2 className="h-4 w-4" />} value={route.localPort == null ? '未配置' : route.enabled ? configuredBinding : '停用中，不监听代理端口'} title={configuredBinding || '未配置'} />
        <Info label="默认地址（兜底）" icon={<Globe2 className="h-4 w-4" />} value={displayTargetUrl(route.targetUrl)} title={route.targetUrl} />
        <Info label="代理地址" icon={<ScrollText className="h-4 w-4" />} value={route.accessPageBaseUrl ? displayTargetUrl(route.accessPageBaseUrl) : '未配置'} title={route.accessPageBaseUrl || '未配置'} />
        <Info label="配置文件" icon={<FileJson className="h-4 w-4" />} value={`${route.id}.json`} title={`config/routes/${route.id}.json`} />
      </div>

      <div className="relative mt-4">
        <span className="text-xs font-black uppercase tracking-wider text-clay-muted">路径前缀</span>
        <div className="mt-2 flex flex-wrap gap-2">
          {prefixes.length === 0 ? <Badge variant="yellow">未配置路径前缀，请求走默认地址</Badge> : prefixes.map((prefix, index) => <Badge key={prefix} variant="default" className={cn('route-prefix-chip route-prefix-chip-interactive', prefixToneClass(index))}>{prefix}</Badge>)}
        </div>
      </div>

      <div className="route-card-actions-grid relative mt-5">
        <Button className="route-action route-action-view" size="sm" variant="outline" onClick={onView}><Eye className="h-4 w-4" />查看</Button>
        <Button className="route-action route-action-copy" size="sm" variant="outline" onClick={onCopy}><Copy className="h-4 w-4" />拷贝</Button>
        <Button className="route-action route-action-access" size="sm" variant="primary" onClick={onAccess} disabled={!canAccess} title={!canAccess ? '请先启用路由并填写监听端口和访问页' : '新标签页打开访问页'}>访问</Button>
        <Button className="route-action route-action-delete" size="sm" variant="danger" onClick={onDelete}><Trash2 className="h-4 w-4" />删除</Button>
      </div>
      </div>
    </Card>
  );
}

function Info({ label, value, title, icon }: { label: string; value: string; title?: string; icon: React.ReactNode }) {
  return (
    <div className="route-info-card p-1">
      <div className="flex items-center gap-1.5 text-[11px] font-black uppercase tracking-wider text-clay-muted">{icon}{label}</div>
      <div className="mt-1 truncate text-sm font-black text-clay-ink" title={title || value}>{value}</div>
    </div>
  );
}
