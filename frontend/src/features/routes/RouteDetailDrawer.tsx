import * as React from 'react';
import { ArrowLeft, ArrowRight, FileJson, GitBranch, MousePointer2, Power, Server } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Textarea } from '@/components/ui/textarea';
import { RouteFormPanel } from './RouteFormDialog';
import { RouteLogPanel } from '@/features/logs/RouteLogDialog';
import type { RouteConfig, RouteConfigPayload, RouteFormValues } from './types';
import { routeToFormValues } from './route-utils';
import { buildRouteTopologyModel, routeStatusLabel, routeStatusTone } from './route-detail-utils';

interface RouteDetailDrawerProps {
  open: boolean;
  route: RouteConfig | null;
  fileName: string;
  content: string;
  loading: boolean;
  error: string;
  onOpenChange: (open: boolean) => void;
  onSaveRoute: (payload: RouteConfigPayload, mode: 'edit', route: RouteConfig) => Promise<void>;
  existingNames: string[];
  existingBindings: string[];
  onAccess: (route: RouteConfig) => void;
  onDelete: (route: RouteConfig) => void;
}

const DETAIL_FORM_ID = 'route-detail-config-form';

export function RouteDetailDrawer({ open, route, fileName, content, loading, error, onOpenChange, onSaveRoute, existingNames, existingBindings }: RouteDetailDrawerProps) {
  const [draftValues, setDraftValues] = React.useState<RouteFormValues | null>(null);
  const [jsonEditing, setJsonEditing] = React.useState(false);
  const [jsonEditValue, setJsonEditValue] = React.useState('');
  const [jsonError, setJsonError] = React.useState('');

  React.useEffect(() => {
    setDraftValues(null);
    setJsonEditing(false);
    setJsonEditValue('');
    setJsonError('');
  }, [route?.id, open]);

  if (!open || !route) {
    return null;
  }

  const statusTone = routeStatusTone(route);
  const liveValues = draftValues || routeToFormValues(route);
  const jsonPreview = draftValues ? formatDraftJson(route, draftValues) : content || '{}';
  const jsonDisplayValue = jsonEditing ? jsonEditValue : formatJsonForDisplay(jsonPreview);

  function startJsonEdit() {
    setJsonEditValue(formatJsonText(jsonPreview));
    setJsonError('');
    setJsonEditing(true);
  }

  async function saveJsonDraft() {
    try {
      const formatted = formatJsonText(jsonEditValue);
      const parsed = JSON.parse(formatted) as unknown;
      const payload = jsonToPayload(parsed);
      await onSaveRoute(payload, 'edit', route);
      setJsonEditValue(formatted);
      setJsonEditing(false);
      setJsonError('');
    } catch (saveError) {
      setJsonError(saveError instanceof SyntaxError ? 'JSON 格式不正确，请检查后再保存' : saveError instanceof Error ? saveError.message : 'JSON 保存失败');
    }
  }

  return (
    <section className="route-detail-page chunky-panel bg-[#DCEBFF] p-4 sm:p-6" aria-labelledby="route-detail-title">
      <div className="mb-5 flex flex-wrap items-start justify-between gap-4">
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
          </div>
        </div>
        <Button variant="outline" aria-label="返回路由列表" onClick={() => onOpenChange(false)}>
          <ArrowLeft className="h-5 w-5" />
          返回列表
        </Button>
      </div>

      {error && <div className="mb-4 rounded-2xl border-[3px] border-clay-border bg-[#FFD9D3] p-3 text-sm font-black text-clay-ink">{error}</div>}

      <Tabs defaultValue="edit" className="route-detail-tabs mt-5">
        <div className="route-detail-tabs-bar">
          <TabsList className="route-detail-tabs-list items-stretch">
            <TabsTrigger className="route-detail-tab-trigger" value="edit">配置编辑</TabsTrigger>
            <TabsTrigger className="route-detail-tab-trigger" value="json">JSON 配置</TabsTrigger>
            <TabsTrigger className="route-detail-tab-trigger" value="logs">实时日志</TabsTrigger>
          </TabsList>
        </div>

        <TabsContent className="route-detail-tab-content" value="edit">
          <div className="route-detail-edit-layout grid gap-4">
            <section className="rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
              <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="text-lg font-black text-clay-ink">配置编辑</h3>
                  <p className="text-sm font-bold text-clay-muted">左侧修改配置，JSON 配置标签页会实时同步；保存后刷新代理并更新当前详情。</p>
                </div>
                <Button className="shrink-0" type="submit" form={DETAIL_FORM_ID} variant="orange">保存配置</Button>
              </div>
              <div className="route-detail-edit-grid grid gap-4">
                <RouteFormPanel
                  active={open}
                  mode="edit"
                  route={route}
                  existingNames={existingNames}
                  existingBindings={existingBindings}
                  onSubmit={(payload) => onSaveRoute(payload, 'edit', route)}
                  onValuesChange={setDraftValues}
                  submitLabel="保存配置"
                  formId={DETAIL_FORM_ID}
                  showActions={false}
                />
              </div>
            </section>
            <RouteTopologyGraph route={route} values={liveValues} />
          </div>
        </TabsContent>

        <TabsContent className="route-detail-tab-content" value="json">
          <div className="route-detail-edit-layout route-detail-json-layout grid gap-4">
            <section className="route-detail-json-panel rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
              <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="text-lg font-black text-clay-ink">JSON 配置</h3>
                  <p className="text-sm font-bold text-clay-muted">预览会随表单实时更新；编辑后可自动排版并通过现有路由 API 保存。</p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {jsonEditing ? (
                    <>
                      <Button variant="orange" onClick={() => void saveJsonDraft()} disabled={loading}>保存 JSON</Button>
                      <Button variant="outline" onClick={() => { setJsonEditing(false); setJsonError(''); }}>取消</Button>
                    </>
                  ) : (
                    <Button variant="primary" onClick={startJsonEdit} disabled={loading}>编辑 JSON</Button>
                  )}
                </div>
              </div>
              {jsonError && <div className="mb-3 rounded-2xl border-[3px] border-clay-border bg-[#FFD9D3] p-3 text-sm font-black text-clay-ink">{jsonError}</div>}
              <Textarea
                className="route-detail-json-editor min-h-[560px] bg-[#E3F8EE] font-mono text-sm leading-6"
                value={loading ? '正在加载配置...' : jsonDisplayValue}
                onChange={(event) => setJsonEditValue(event.target.value)}
                readOnly={!jsonEditing || loading}
                spellCheck={false}
              />
            </section>
            <RouteTopologyGraph route={route} values={liveValues} />
          </div>
        </TabsContent>

        <TabsContent className="route-detail-tab-content" value="logs">
          <section className="rounded-[24px] border-[3px] border-clay-border bg-white p-4 shadow-clay-sm">
            <RouteLogPanel open={open} route={route} />
          </section>
        </TabsContent>
      </Tabs>
    </section>
  );
}

function RouteTopologyGraph({ route, values, compact = false }: { route: RouteConfig; values: RouteFormValues; compact?: boolean }) {
  const topology = buildRouteTopologyModel(values);

  return (
    <section className={compact ? 'route-topology route-topology-compact rounded-[24px] border-[3px] border-clay-border bg-[#FFF7D6] p-3 shadow-clay-sm' : 'route-topology rounded-[24px] border-[3px] border-clay-border bg-[#FFF7D6] p-4 shadow-clay-sm'} aria-label="动态拓扑关系图">
      {!compact && (
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="text-lg font-black text-clay-ink">请求流向图</h3>
            <p className="text-sm font-bold text-clay-muted">入口 → 前缀开关 → 命中代理 / 未命中兜底，随表单实时变化。</p>
          </div>
          <Badge variant={values.enabled ? 'mint' : 'muted'}>{topology.statusText}</Badge>
        </div>
      )}
      <div className="topology-map" role="img" aria-label={`${route.name} 的请求流向图`}>
        <TopologyEndpoint
          tone="blue"
          icon={<MousePointer2 className="h-5 w-5" />}
          label="入口"
          value={topology.ingress.value}
          note={topology.ingress.note}
        />
        <TopologyFlowArrow />
        <div className="topology-router-card" aria-label="路径前缀开关">
          <span className="topology-router-icon" aria-hidden="true">
            <GitBranch className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <span className="topology-kicker">前缀开关</span>
            <strong title={topology.router.summary}>{topology.router.summary}</strong>
          </div>
          <div className="topology-prefix-chips" aria-label="路径前缀">
            {topology.router.prefixChips.map((prefix) => (
              <span key={prefix} title={prefix}>{prefix}</span>
            ))}
          </div>
        </div>
        <div className="topology-branches" aria-label="分支目标">
          <TopologyBranch
            tone="mint"
            active={topology.hit.active}
            badge="命中"
            targetLabel="代理"
            target={topology.hit.target}
            note={topology.hit.note}
          />
          <TopologyBranch
            tone="pink"
            active
            badge="未命中"
            targetLabel="兜底"
            target={topology.miss.target}
            note={topology.miss.note}
          />
        </div>
      </div>
    </section>
  );
}
function TopologyEndpoint({ tone, icon, label, value, note }: { tone: 'blue' | 'yellow' | 'mint' | 'pink'; icon: React.ReactNode; label: string; value: string; note: string }) {
  return (
    <div className={`topology-card topology-card-${tone}`}>
      <span className="topology-icon" aria-hidden="true">{icon}</span>
      <span className="topology-kicker">{label}</span>
      <strong title={value}>{value}</strong>
      <small>{note}</small>
    </div>
  );
}

function TopologyFlowArrow() {
  return (
    <div className="topology-flow-arrow" aria-hidden="true">
      <ArrowRight className="h-5 w-5" />
    </div>
  );
}

function TopologyBranch({ tone, active, badge, targetLabel, target, note }: { tone: 'mint' | 'pink'; active: boolean; badge: string; targetLabel: string; target: string; note: string }) {
  return (
    <div className={`topology-branch topology-branch-${tone}${active ? '' : ' topology-branch-muted'}`}>
      <div className="topology-branch-tag">
        <span aria-hidden="true" />
        <strong>{badge}</strong>
      </div>
      <div className="topology-branch-target">
        <span className="topology-icon" aria-hidden="true"><Server className="h-5 w-5" /></span>
        <div className="min-w-0">
          <span className="topology-kicker">{targetLabel}</span>
          <strong title={target}>{target}</strong>
          <small>{note}</small>
        </div>
      </div>
    </div>
  );
}

function normalizePreviewUrl(value?: string | null): string | null {
  const text = (value || '').trim();
  if (!text) {
    return null;
  }
  return /^https?:\/\//.test(text) ? text : 'http://' + text;
}

function formatJsonText(value: string): string {
  return JSON.stringify(JSON.parse(value), null, 2);
}

function formatJsonForDisplay(value: string): string {
  try {
    return formatJsonText(value);
  } catch {
    return value;
  }
}

function jsonToPayload(value: unknown): RouteConfigPayload {
  const routeJson = value as Partial<RouteConfig>;
  const prefixes = Array.isArray(routeJson.pathPrefixes) ? routeJson.pathPrefixes : routeJson.pathPrefix ? [routeJson.pathPrefix] : [];
  return {
    name: String(routeJson.name ?? ''),
    pathPrefix: prefixes[0] || null,
    pathPrefixes: prefixes.filter((prefix): prefix is string => typeof prefix === 'string'),
    targetUrl: String(routeJson.targetUrl ?? ''),
    accessPageBaseUrl: routeJson.accessPageBaseUrl == null ? null : String(routeJson.accessPageBaseUrl),
    accessPage: routeJson.accessPage == null ? null : String(routeJson.accessPage),
    localIp: routeJson.localIp == null ? null : String(routeJson.localIp),
    localPort: typeof routeJson.localPort === 'number' ? routeJson.localPort : routeJson.localPort == null ? null : Number(routeJson.localPort),
    enabled: routeJson.enabled === true,
  };
}

function formatDraftJson(route: RouteConfig, values: RouteFormValues): string {
  const prefixes = values.pathPrefixes || [];
  return JSON.stringify({
    id: route.id,
    name: values.name.trim(),
    pathPrefix: prefixes[0] || null,
    pathPrefixes: prefixes,
    targetUrl: normalizePreviewUrl(values.targetUrl) || '',
    accessPageBaseUrl: normalizePreviewUrl(values.accessPageBaseUrl),
    accessPage: values.accessPage.trim() || null,
    localIp: values.localIp.trim() || '127.0.0.1',
    localPort: values.localPort.trim() ? Number(values.localPort.trim()) : null,
    enabled: values.enabled === true,
  }, null, 2);
}
