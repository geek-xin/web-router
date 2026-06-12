import { Copy, Edit3, Eye, FileJson, Globe2, MousePointer2, Power, ScrollText, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import type { RouteConfig } from './types';
import { activeLocalBinding, displayTargetUrl, effectivePathPrefixes, hasLocalPort, localBinding, routeAccessUrl } from './route-utils';
import { cn } from '@/lib/utils';

const cardThemes = ['chunky-card-pink', 'chunky-card-mint', 'chunky-card-blue', 'chunky-card-yellow'];

interface RouteCardProps {
  route: RouteConfig;
  index: number;
  selected: boolean;
  onSelectedChange: (selected: boolean) => void;
  onView: () => void;
  onLogs: () => void;
  onCopy: () => void;
  onEdit: () => void;
  onAccess: () => void;
  onToggle: () => void;
  onDelete: () => void;
}

export function RouteCard({ route, index, selected, onSelectedChange, onView, onLogs, onCopy, onEdit, onAccess, onToggle, onDelete }: RouteCardProps) {
  const prefixes = effectivePathPrefixes(route);
  const activeBinding = activeLocalBinding(route);
  const configuredBinding = localBinding(route.localIp, route.localPort);
  const accessUrl = routeAccessUrl({ localBinding: activeBinding, accessPage: route.accessPage, pathPrefixes: prefixes });
  const canAccess = Boolean(accessUrl);
  const canToggle = route.enabled || hasLocalPort(route);
  const theme = cardThemes[index % cardThemes.length];

  return (
    <Card className={cn('group relative overflow-hidden p-0 transition-all duration-200 hover:-translate-y-1 hover:shadow-clay-hover', theme, selected && 'ring-4 ring-clay-primary/40')}>
      <div className="flex h-[72px] items-center justify-between border-b-[3px] border-clay-border px-5">
        <div className="flex h-12 w-12 items-center justify-center rounded-full border-[3px] border-clay-border bg-clay-primary text-lg font-black text-white shadow-clay-sm">
          {index + 1}
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={route.enabled ? 'mint' : 'muted'}>{route.enabled ? '运行中' : '已停用'}</Badge>
          <span className="text-sm font-black text-clay-ink">{route.enabled ? '代理可用' : '不监听端口'}</span>
        </div>
      </div>
      <div className="relative bg-white p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <Checkbox checked={selected} onCheckedChange={(value) => onSelectedChange(value === true)} aria-label={`选择路由 ${route.name}`} />
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="max-w-[260px] truncate text-2xl font-black text-clay-ink" title={route.name}>{route.name}</h3>
            </div>
            <p className="mt-1 text-sm font-bold text-clay-muted">{route.id}.json</p>
          </div>
        </div>
        <Button size="sm" variant={route.enabled ? 'danger' : 'primary'} onClick={onToggle} disabled={!canToggle} title={!canToggle ? '请先编辑路由并填写监听端口后再启用' : undefined}>
          <Power className="h-4 w-4" />
          {route.enabled ? '停用' : '启用'}
        </Button>
      </div>

      <div className="relative mt-5 grid gap-3 md:grid-cols-2">
        <Info label="监听地址" icon={<MousePointer2 className="h-4 w-4" />} value={route.localPort == null ? '未配置' : route.enabled ? configuredBinding : '停用中，不监听代理端口'} title={configuredBinding || '未配置'} />
        <Info label="默认地址（兜底）" icon={<Globe2 className="h-4 w-4" />} value={displayTargetUrl(route.targetUrl)} title={route.targetUrl} />
        <Info label="代理地址" icon={<ScrollText className="h-4 w-4" />} value={route.accessPageBaseUrl ? displayTargetUrl(route.accessPageBaseUrl) : '未配置'} title={route.accessPageBaseUrl || '未配置'} />
        <Info label="配置文件" icon={<FileJson className="h-4 w-4" />} value={`${route.id}.json`} title={`config/routes/${route.id}.json`} />
      </div>

      <div className="relative mt-4">
        <span className="text-xs font-black uppercase tracking-wider text-clay-muted">路径前缀</span>
        <div className="mt-2 flex flex-wrap gap-2">
          {prefixes.length === 0 ? <Badge variant="yellow">未配置路径前缀，请求走默认地址</Badge> : prefixes.map((prefix) => <Badge key={prefix} variant="indigo">{prefix}</Badge>)}
        </div>
      </div>

      <div className="relative mt-5 flex flex-wrap gap-2">
        <Button size="sm" variant="outline" onClick={onView}><Eye className="h-4 w-4" />查看</Button>
        <Button size="sm" variant="outline" onClick={onLogs}><ScrollText className="h-4 w-4" />日志</Button>
        <Button size="sm" variant="outline" onClick={onCopy}><Copy className="h-4 w-4" />拷贝</Button>
        <Button size="sm" variant="outline" onClick={onEdit}><Edit3 className="h-4 w-4" />编辑</Button>
        <Button size="sm" variant="primary" onClick={onAccess} disabled={!canAccess} title={!canAccess ? '请先启用路由并填写监听端口和访问页' : '新标签页打开访问页'}>访问</Button>
        <Button size="sm" variant="danger" onClick={onDelete}><Trash2 className="h-4 w-4" />删除</Button>
      </div>
      </div>
    </Card>
  );
}

function Info({ label, value, title, icon }: { label: string; value: string; title?: string; icon: React.ReactNode }) {
  return (
    <div className="rounded-2xl border-[3px] border-clay-border bg-[#F8F6F3] p-3 shadow-clay-sm">
      <div className="flex items-center gap-2 text-xs font-black uppercase tracking-wider text-clay-muted">{icon}{label}</div>
      <div className="mt-1 truncate text-sm font-black text-clay-ink" title={title || value}>{value}</div>
    </div>
  );
}
