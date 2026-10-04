import { Check, Copy, Eye, Globe2, ScrollText, Trash2 } from 'lucide-react';
import { Checkbox } from '@/components/ui/checkbox';
import type { RouteConfig } from './types';
import type { RouteTrafficMetrics } from './route-metrics';
import { formatCompactNumber, formatLatency } from './route-metrics';
import {
  activeLocalBinding,
  deriveRouteStatus,
  displayTargetUrl,
  effectivePathPrefixes,
  hasLocalPort,
  routeAccessUrl,
  routeAccentClass,
  routeBehaviorSummary,
  routeStatusDotClass,
  routeStatusText,
  visiblePathPrefixes,
} from './route-utils';
import { RouteSparkline } from './RouteSparkline';
import { cn } from '@/lib/utils';

interface RouteListProps {
  routes: RouteConfig[];
  metricsById: Record<string, RouteMetricsById>;
  selectedIds: string[];
  onSelectedChange: (routeId: string, selected: boolean) => void;
  onView: (route: RouteConfig) => void;
  onLogs: (route: RouteConfig) => void;
  onCopy: (route: RouteConfig) => void;
  onAccess: (route: RouteConfig) => void;
  onToggle: (route: RouteConfig) => void;
  onDelete: (route: RouteConfig) => void;
}

type RouteMetricsById = RouteTrafficMetrics | undefined;

/**
 * 路由列表视图：与卡片视图共享同一套数据、强调色与操作，
 * 只是把每张卡片压成一行，便于一次浏览大量路由。
 */
export function RouteList({ routes, metricsById, selectedIds, onSelectedChange, onView, onLogs, onCopy, onAccess, onToggle, onDelete }: RouteListProps) {
  return (
    <div className="route-list" role="list" aria-label="路由列表视图">
      <div className="route-list-head" role="presentation">
        <span className="route-list-head-cell">路由</span>
        <span className="route-list-head-cell">Path</span>
        <span className="route-list-head-cell">Target / 本地端口</span>
        <span className="route-list-head-cell">请求 / min</span>
        <span className="route-list-head-cell">延迟</span>
        <span className="route-list-head-cell route-list-spark">30 分钟</span>
        <span className="route-list-head-cell" style={{ textAlign: 'right' }}>操作</span>
      </div>

      {routes.map((route, index) => {
        const prefixes = effectivePathPrefixes(route);
        const { chips, more } = visiblePathPrefixes(prefixes);
        const metrics = metricsById[route.id];
        const status = deriveRouteStatus(route, metrics);
        const binding = activeLocalBinding(route);
        const canAccess = Boolean(routeAccessUrl({ localBinding: binding, accessPage: route.accessPage, pathPrefixes: prefixes }));
        const canToggle = route.enabled || hasLocalPort(route);
        const selected = selectedIds.includes(route.id);

        return (
          <article
            key={route.id}
            role="listitem"
            className={cn('route-list-row', routeAccentClass(index), selected && 'route-list-row-selected', !route.enabled && 'route-list-row-muted')}
            aria-label={'路由 ' + route.name}
          >
            <span className="route-list-main">
              <Checkbox
                checked={selected}
                onCheckedChange={(value) => onSelectedChange(route.id, value === true)}
                aria-label={'选择路由 ' + route.name}
              />
              <span className="route-card-icon" aria-hidden="true">
                <RouteGlyph enabled={route.enabled} />
              </span>
              <span className="min-w-0">
                <span className="route-list-name" title={route.name}>{route.name}</span>
                <span className="route-list-desc" title={routeBehaviorSummary(route)}>
                  <span className={cn('dot mr-1.5 inline-block align-middle', routeStatusDotClass(status))} aria-hidden="true" />
                  {routeStatusText(status)} · {routeBehaviorSummary(route)}
                </span>
              </span>
            </span>

            <span className="route-list-paths">
              {chips.length === 0
                ? <span className="route-prefix-chip">全部兜底</span>
                : chips.map((prefix) => <span key={prefix} className="route-prefix-chip">{prefix}</span>)}
              {more > 0 && <span className="route-prefix-chip route-prefix-chip-more">+{more}</span>}
            </span>

            <span className="route-list-targets">
              <span className="route-list-stacked-label">Target / 本地端口</span>
              <span className="route-field-value" title={route.targetUrl}>{displayTargetUrl(route.targetUrl) || '未配置'}</span>
              <span className="route-field-value route-list-port" title={binding || '未监听'}>
                {route.localPort == null ? '—' : route.enabled ? route.localPort : route.localPort + '（停用）'}
              </span>
            </span>

            <span className="route-list-metrics">
              <span className="route-list-num">
                <span className="route-list-stacked-label">请求 / min</span>
                {metrics ? formatCompactNumber(metrics.requestsLastMinute) : '—'}
              </span>
              <span className="route-list-num">
                <span className="route-list-stacked-label">延迟</span>
                {metrics ? formatLatency(metrics.averageDurationMs) : '—'}
              </span>
            </span>

            <span className="route-list-spark">
              <RouteSparkline values={metrics?.trafficBuckets} idle={!route.enabled} label={route.name + ' 最近 30 分钟请求数'} />
            </span>

            <span className="route-list-actions">
              <button type="button" className="route-card-action" onClick={() => onView(route)} title="打开路由详情" aria-label={'查看 ' + route.name + ' 详情'}>
                <Eye className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">查看</span>
              </button>
              <button type="button" className="route-card-action" onClick={() => onLogs(route)} title="查看请求日志" aria-label={'查看 ' + route.name + ' 日志'}>
                <ScrollText className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">日志</span>
              </button>
              <button type="button" className="route-card-action" onClick={() => onCopy(route)} title="拷贝为新路由" aria-label={'拷贝 ' + route.name}>
                <Copy className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">拷贝</span>
              </button>
              <button
                type="button"
                className="route-card-action"
                onClick={() => onAccess(route)}
                disabled={!canAccess}
                title={canAccess ? '新标签页打开访问页' : '请先启用路由并填写监听端口和访问页'}
                aria-label={'访问 ' + route.name}
              >
                <Globe2 className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">访问</span>
              </button>
              <button
                type="button"
                className="route-card-action"
                onClick={() => onToggle(route)}
                disabled={!canToggle}
                title={canToggle ? (route.enabled ? '停用该路由' : '启用该路由') : '请先编辑路由并填写监听端口后再启用'}
                aria-label={route.enabled ? '停用 ' + route.name : '启用 ' + route.name}
              >
                {route.enabled ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <span className="h-3.5 w-3.5 rounded-full border border-current" aria-hidden="true" />}
                <span className="route-card-action-label">{route.enabled ? '停用' : '启用'}</span>
              </button>
              <button type="button" className="route-card-action route-card-action-danger" onClick={() => onDelete(route)} title="删除该路由" aria-label={'删除 ' + route.name}>
                <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </span>
          </article>
        );
      })}
    </div>
  );
}

/** 品牌符号：节点 + 连线，与卡片视图保持一致。 */
function RouteGlyph({ enabled }: { enabled: boolean }) {
  return (
    <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true">
      <path d="M6 7h12" opacity={enabled ? 1 : 0.6} />
      <path d="M6 7l6 10" opacity={enabled ? 1 : 0.6} />
      <path d="M18 7l-6 10" opacity={enabled ? 1 : 0.6} />
      <circle cx="6" cy="7" r="2.2" fill="var(--surface-panel)" />
      <circle cx="18" cy="7" r="2.2" fill="var(--surface-panel)" />
      <circle cx="12" cy="17" r="2.2" fill="var(--surface-panel)" />
    </svg>
  );
}
