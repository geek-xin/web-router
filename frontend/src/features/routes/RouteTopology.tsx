import { ArrowDown, ArrowRight, CornerDownRight, MonitorSmartphone, Network, Server, Waypoints } from 'lucide-react';
import type { RouteTopologyModel } from './route-detail-utils';
import type { RouteTrafficMetrics } from './route-metrics';
import { cn } from '@/lib/utils';

interface RouteTopologyProps {
  model: RouteTopologyModel;
  /** 停用路由：关闭流动动画，见设计系统 §7.3。 */
  idle?: boolean;
  /** 实时流量：驱动流动速度与色调，让拓扑反映当前链路状况。 */
  traffic?: RouteTrafficMetrics | null;
  className?: string;
}

/**
 * 品牌核心图形：Client → wrouter → 本地端口 → 目标服务。
 * 顺序与语义固定，不随数据变化调整。
 *
 * <p><b>实时画面</b>：连线用 SVG 虚线沿链路方向流动，速度由最近一分钟请求数决定——
 * 请求越多流得越快；出现失败请求时整条链路转为告警色；路由停用则完全静止。
 * 这样一眼就能看出「这条链路现在有没有流量、通不通」。</p>
 *
 * <p>刻意不用粒子 / 3D / 渐变：设计系统 §7 明确禁止这几类效果（见 §7 动效）。</p>
 */
export function RouteTopology({ model, idle = false, traffic, className }: RouteTopologyProps) {
  const active = model.active && !idle;
  const flow = describeFlow(traffic, active);

  return (
    <div
      className={cn('topology', flow.toneClass, className)}
      style={{ '--flow-duration': flow.duration } as React.CSSProperties}
      role="img"
      aria-label={'请求链路：' + model.client.value + ' → wrouter ' + model.gateway.value + ' → ' + model.localPort.value + ' → ' + model.target.value + '。' + flow.aria}
    >
      <TopologyNode
        tone="client"
        icon={<MonitorSmartphone className="h-3.5 w-3.5" />}
        kicker={model.client.kicker}
        value={model.client.value}
        note={model.client.note}
      />
      <TopologyLink active={active} />
      <TopologyNode
        tone="gateway"
        icon={<Network className="h-3.5 w-3.5" />}
        kicker={model.gateway.kicker}
        value={model.gateway.value}
        note={model.gateway.note}
      />
      <TopologyLink active={active} />
      <TopologyNode
        tone="port"
        icon={<Waypoints className="h-3.5 w-3.5" />}
        kicker={model.localPort.kicker}
        value={model.localPort.value}
        note={model.localPort.note}
      />
      <TopologyLink active={active} />
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

interface FlowDescription {
  /** CSS 动画时长；越小流得越快。 */
  duration: string;
  /** 色调类名：正常走主色，有失败请求走告警色。 */
  toneClass: string;
  /** 给屏幕阅读器的实时描述。 */
  aria: string;
}

/**
 * 把最近一分钟的流量折算成「流动速度 + 色调」。
 *
 * <p>分档而不是线性映射：线性会让速度差异过小、看起来像随机抖动，
 * 而分档能让人明确区分「没流量 / 少量 / 繁忙 / 高峰」四种状态。</p>
 */
export function describeFlow(traffic: RouteTrafficMetrics | null | undefined, active: boolean): FlowDescription {
  if (!active) {
    return { duration: '0s', toneClass: 'topology-flow-idle', aria: '链路已停用，无流量。' };
  }
  const requests = Math.max(0, traffic?.requestsLastMinute ?? 0);
  const failed = Math.max(0, traffic?.failedLastMinute ?? 0);
  const toneClass = failed > 0 ? 'topology-flow-error' : 'topology-flow-live';

  if (requests === 0) {
    return { duration: '2.6s', toneClass, aria: '最近一分钟无请求。' };
  }
  if (requests < 10) {
    return { duration: '1.8s', toneClass, aria: '最近一分钟 ' + requests + ' 次请求。' };
  }
  if (requests < 100) {
    return { duration: '1.1s', toneClass, aria: '最近一分钟 ' + requests + ' 次请求，流量正常。' };
  }
  return { duration: '0.7s', toneClass, aria: '最近一分钟 ' + requests + ' 次请求，流量高峰。' };
}

function TopologyNode({ tone, icon, kicker, value, note }: { tone: 'client' | 'gateway' | 'port' | 'target'; icon: React.ReactNode; kicker: string; value: string; note: string }) {
  return (
    <div className={cn('topology-node', 'topology-node-' + tone)}>
      <span className="topology-node-icon" aria-hidden="true">{icon}</span>
      <span className="topology-node-body">
        <span className="topology-node-kicker">{kicker}</span>
        <span className="topology-node-value" title={value}>{value}</span>
        <span className="topology-node-note" title={note}>{note}</span>
      </span>
    </div>
  );
}

/**
 * 连接段：一条静止底线 + 一条沿链路方向流动的虚线，末端带方向箭头。
 *
 * <p>用 SVG 的 `stroke-dasharray` + `stroke-dashoffset` 做流动：
 * 虚线整体平移就是「线条在流动」，且能天然表达方向。
 * 相比渐变或粒子，它不触发设计系统 §7 的禁令，也不产生额外 DOM 节点。</p>
 */
function TopologyLink({ active }: { active: boolean }) {
  return (
    <span className={cn('topology-link', active && 'topology-link-active')} aria-hidden="true">
      {/* 纵向（窄屏堆叠）与横向（宽屏四列）各一条线，由 CSS 决定显示哪条。
          non-scaling-stroke 让线宽不随容器拉伸变形。 */}
      <svg className="topology-link-svg topology-link-svg-v" viewBox="0 0 24 24" preserveAspectRatio="none" focusable="false">
        <line className="topology-link-base" x1="12" y1="0" x2="12" y2="24" vectorEffect="non-scaling-stroke" />
        <line className="topology-link-flow" x1="12" y1="0" x2="12" y2="24" vectorEffect="non-scaling-stroke" />
      </svg>
      <svg className="topology-link-svg topology-link-svg-h" viewBox="0 0 24 24" preserveAspectRatio="none" focusable="false">
        <line className="topology-link-base" x1="0" y1="12" x2="24" y2="12" vectorEffect="non-scaling-stroke" />
        <line className="topology-link-flow" x1="0" y1="12" x2="24" y2="12" vectorEffect="non-scaling-stroke" />
      </svg>
      <ArrowDown className="topology-link-arrow topology-link-arrow-down" />
      <ArrowRight className="topology-link-arrow topology-link-arrow-right" />
    </span>
  );
}
