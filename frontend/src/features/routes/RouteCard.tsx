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

interface RouteCardProps {
  route: RouteConfig;
  index: number;
  selected: boolean;
  metrics?: RouteTrafficMetrics | null;
  onSelectedChange: (selected: boolean) => void;
  onView: () => void;
  onLogs: () => void;
  onCopy: () => void;
  onAccess: () => void;
  onToggle: () => void;
  onDelete: () => void;
}

export function RouteCard({ route, index, selected, metrics, onSelectedChange, onView, onLogs, onCopy, onAccess, onToggle, onDelete }: RouteCardProps) {
  const prefixes = effectivePathPrefixes(route);
  const { chips, more } = visiblePathPrefixes(prefixes);
  const activeBinding = activeLocalBinding(route);
  const accessUrl = routeAccessUrl({ localBinding: activeBinding, accessPage: route.accessPage, pathPrefixes: prefixes });
  const canAccess = Boolean(accessUrl);
  const canToggle = route.enabled || hasLocalPort(route);
  const status = deriveRouteStatus(route, metrics);
  const summary = routeBehaviorSummary(route);
  const hasTraffic = Boolean(metrics);

  return (
    <article
      className={cn('route-card', routeAccentClass(index), selected && 'route-card-selected', !route.enabled && 'route-card-muted')}
      aria-label={'路由 ' + route.name}
    >
      <div className="route-card-head">
        <Checkbox
          className="mt-2"
          checked={selected}
          onCheckedChange={(value) => onSelectedChange(value === true)}
          aria-label={'选择路由 ' + route.name}
        />
        <span className="route-card-icon" aria-hidden="true">
          <RouteGlyph enabled={route.enabled} />
        </span>
        <div className="route-card-title">
          <h3 className="route-card-name" title={route.name}>{route.name}</h3>
          <p className="route-card-desc" title={summary}>{summary}</p>
        </div>
        <span className="route-card-status">
          <span className={cn('dot', routeStatusDotClass(status))} aria-hidden="true" />
          {routeStatusText(status)}
        </span>
      </div>

      <div className="route-card-body">
        <div className="route-card-path" title={prefixes.join('  ') || '未配置路径前缀'}>
          {chips.length === 0 ? (
            <span className="route-prefix-chip">全部兜底</span>
          ) : (
            chips.map((prefix) => <span key={prefix} className="route-prefix-chip">{prefix}</span>)
          )}
          {more > 0 && <span className="route-prefix-chip route-prefix-chip-more">+{more}</span>}
        </div>

        <div className="route-card-meta">
          <div className="min-w-0">
            <span className="route-field-label">Target</span>
            <span className="route-field-value" title={route.targetUrl}>{displayTargetUrl(route.targetUrl) || '未配置'}</span>
          </div>
          <div className="min-w-0">
            <span className="route-field-label">本地端口</span>
            <span className="route-field-value" title={activeBinding || '未监听'}>
              {route.localPort == null ? '—' : route.enabled ? route.localPort : route.localPort + '（停用）'}
            </span>
          </div>
        </div>

        <div className="route-card-metrics">
          <div className="route-metric">
            <span className="route-metric-figure">
              <span className="route-metric-value">{hasTraffic ? formatCompactNumber(metrics?.requestsLastMinute) : '—'}</span>
              {hasTraffic && <span className="route-metric-unit">/ min</span>}
            </span>
            <span className="route-metric-caption">请求数</span>
          </div>
          <div className="route-metric">
            <span className="route-metric-figure">
              <span className="route-metric-value">{hasTraffic ? formatLatency(metrics?.averageDurationMs) : '—'}</span>
            </span>
            <span className="route-metric-caption">平均延迟</span>
          </div>
        </div>

        <div className="route-card-spark">
          <RouteSparkline
            values={metrics?.trafficBuckets}
            idle={!route.enabled}
            label={route.name + ' 最近 30 分钟请求数'}
          />
        </div>
      </div>

      <div className="route-card-actions">
        <button type="button" className="route-card-action" onClick={onView} title="打开路由详情" aria-label={'查看 ' + route.name + ' 详情'}>
          <Eye className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">查看</span>
        </button>
        <button type="button" className="route-card-action" onClick={onLogs} title="查看请求日志" aria-label={'查看 ' + route.name + ' 日志'}>
          <ScrollText className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">日志</span>
        </button>
        <button type="button" className="route-card-action" onClick={onCopy} title="拷贝为新路由" aria-label={'拷贝 ' + route.name}>
          <Copy className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">拷贝</span>
        </button>
        <button
          type="button"
          className="route-card-action"
          onClick={onAccess}
          disabled={!canAccess}
          title={canAccess ? '新标签页打开访问页' : '请先启用路由并填写监听端口和访问页'}
          aria-label={'访问 ' + route.name}
        >
          <Globe2 className="h-3.5 w-3.5" aria-hidden="true" /><span className="route-card-action-label">访问</span>
        </button>
        <button
          type="button"
          className="route-card-action"
          onClick={onToggle}
          disabled={!canToggle}
          title={canToggle ? (route.enabled ? '停用该路由' : '启用该路由') : '请先编辑路由并填写监听端口后再启用'}
          aria-label={route.enabled ? '停用 ' + route.name : '启用 ' + route.name}
        >
          <PowerGlyph enabled={route.enabled} /><span className="route-card-action-label">{route.enabled ? '停用' : '启用'}</span>
        </button>
        <button type="button" className="route-card-action route-card-action-danger" onClick={onDelete} title="删除该路由" aria-label={'删除 ' + route.name}>
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </article>
  );
}

/** 品牌符号：节点 + 连线，对应设计系统 §8。 */
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

function PowerGlyph({ enabled }: { enabled: boolean }) {
  return enabled ? <Check className="h-3.5 w-3.5" /> : <span className="h-3.5 w-3.5 rounded-full border border-current" aria-hidden="true" />;
}
