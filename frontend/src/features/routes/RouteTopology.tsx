import { ArrowDown, CornerDownRight, MonitorSmartphone, Network, Server, Waypoints } from 'lucide-react';
import type { RouteTopologyModel } from './route-detail-utils';
import { cn } from '@/lib/utils';

interface RouteTopologyProps {
  model: RouteTopologyModel;
  /** 停用路由：关闭流动动画，见设计系统 §5.7。 */
  idle?: boolean;
  className?: string;
}

/**
 * 品牌核心图形：Client → wrouter → 本地端口 → 目标服务。
 * 顺序与语义固定，不随数据变化调整。
 */
export function RouteTopology({ model, idle = false, className }: RouteTopologyProps) {
  return (
    <div className={cn('topology-flow', className)} role="img" aria-label={`请求链路：${model.client.value} → wrouter ${model.gateway.value} → ${model.localPort.value} → ${model.target.value}`}>
      <TopologyNode
        tone="client"
        icon={<MonitorSmartphone className="h-3.5 w-3.5" />}
        kicker={model.client.kicker}
        value={model.client.value}
        note={model.client.note}
      />
      <TopologyLink idle={idle} />
      <TopologyNode
        tone="gateway"
        icon={<Network className="h-3.5 w-3.5" />}
        kicker={model.gateway.kicker}
        value={model.gateway.value}
        note={model.gateway.note}
      />
      <TopologyLink idle={idle} />
      <TopologyNode
        tone="port"
        icon={<Waypoints className="h-3.5 w-3.5" />}
        kicker={model.localPort.kicker}
        value={model.localPort.value}
        note={model.localPort.note}
      />
      <TopologyLink idle={idle} />
      <TopologyNode
        tone="target"
        icon={<Server className="h-3.5 w-3.5" />}
        kicker={model.target.kicker}
        value={model.target.value}
        note={model.target.note}
      />
      {model.fallback && (
        <div className="topology-fallback">
          <span className="topology-fallback-label">
            <CornerDownRight className="mr-1 inline h-3 w-3 align-[-1px]" aria-hidden="true" />
            {model.fallback.kicker}
          </span>
          <span className="topology-fallback-value" title={model.fallback.value}>{model.fallback.value}</span>
          <span className="topology-node-note">{model.fallback.note}</span>
        </div>
      )}
    </div>
  );
}

function TopologyNode({ tone, icon, kicker, value, note }: { tone: 'client' | 'gateway' | 'port' | 'target'; icon: React.ReactNode; kicker: string; value: string; note: string }) {
  return (
    <div className={cn('topology-node', `topology-node-${tone}`)}>
      <span className="topology-node-icon" aria-hidden="true">{icon}</span>
      <span className="topology-node-body">
        <span className="topology-node-kicker">{kicker}</span>
        <span className="topology-node-value" title={value}>{value}</span>
        <span className="topology-node-note" title={note}>{note}</span>
      </span>
    </div>
  );
}

function TopologyLink({ idle }: { idle: boolean }) {
  return (
    <span className={cn('topology-link', idle ? 'topology-link-idle' : 'topology-link-flow')} aria-hidden="true">
      <ArrowDown className="relative z-10 h-3 w-3 text-console-ink-subtle" />
    </span>
  );
}
