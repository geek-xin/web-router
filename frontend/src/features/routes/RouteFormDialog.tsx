import * as React from 'react';
import { Plus, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import type { RouteConfig, RouteConfigPayload, RouteFormValues } from './types';
import { nextCopyName, normalizePathPrefix, routeToFormValues, validateRoutePayload } from './route-utils';

interface RouteFormDialogProps {
  open: boolean;
  mode: 'create' | 'edit' | 'copy';
  route?: RouteConfig | null;
  existingNames: string[];
  existingBindings: string[];
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: RouteConfigPayload, mode: 'create' | 'edit' | 'copy', route?: RouteConfig | null) => Promise<void>;
}

const emptyValues: RouteFormValues = {
  name: '',
  targetUrl: '',
  accessPageBaseUrl: '',
  accessPage: '',
  localIp: '127.0.0.1',
  localPort: '',
  pathPrefixes: [],
  enabled: false,
};

export function RouteFormDialog({ open, mode, route, existingNames, existingBindings, onOpenChange, onSubmit }: RouteFormDialogProps) {
  const [values, setValues] = React.useState<RouteFormValues>(emptyValues);
  const [prefixInput, setPrefixInput] = React.useState('');
  const [errors, setErrors] = React.useState<string[]>([]);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    if (mode === 'create' || !route) {
      setValues(emptyValues);
    } else {
      const next = routeToFormValues(route);
      setValues({ ...next, name: mode === 'copy' ? nextCopyName(next.name, existingNames) : next.name, enabled: mode === 'copy' ? false : next.enabled });
    }
    setPrefixInput('');
    setErrors([]);
  }, [open, mode, route, existingNames]);

  function update<K extends keyof RouteFormValues>(key: K, value: RouteFormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }));
  }

  function addPrefix() {
    const normalized = normalizePathPrefix(prefixInput);
    if (!normalized) {
      return;
    }
    if (!/^\/[a-zA-Z0-9_\-/]*$/.test(normalized)) {
      setErrors(['路径前缀只允许英文、数字、下划线、连字符和 /']);
      return;
    }
    update('pathPrefixes', Array.from(new Set([...values.pathPrefixes, normalized])));
    setPrefixInput('');
    setErrors([]);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nameExcludes = mode === 'edit' && route ? existingNames.filter((name) => name !== route.name) : existingNames;
    const bindingExcludes = mode === 'edit' && route ? existingBindings.filter((binding) => binding !== `${route.localIp || '127.0.0.1'}:${route.localPort || ''}`) : existingBindings;
    const validation = validateRoutePayload(values, nameExcludes, bindingExcludes);
    if (validation.errors.length > 0 || !validation.payload) {
      setErrors(validation.errors);
      return;
    }
    setSaving(true);
    try {
      await onSubmit(validation.payload, mode, route);
      onOpenChange(false);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-[min(96vw,900px)]">
        <DialogHeader>
          <DialogTitle>{mode === 'edit' ? '编辑路由' : mode === 'copy' ? '拷贝路由' : '新增路由'}</DialogTitle>
          <DialogDescription>访问监听地址时，命中前缀走代理地址，否则走默认地址。</DialogDescription>
        </DialogHeader>
        <form className="grid gap-5" onSubmit={handleSubmit}>
          {errors.length > 0 && (
            <div className="rounded-2xl border-[3px] border-clay-border bg-[#FFD9D3] p-3 text-sm font-black text-clay-ink">
              {errors.map((error) => <div key={error}>{error}</div>)}
            </div>
          )}
          <div className="grid gap-4 md:grid-cols-3">
            <Field label="路由名称 *" className="md:col-span-3">
              <Input value={values.name} onChange={(event) => update('name', event.target.value)} maxLength={50} placeholder="如 我的服务 / API-测试" />
              <small>最多 50 个字；可输入中文、英文、数字和符号；配置文件名会自动生成</small>
            </Field>
            <Field label="监听 IP">
              <Input value={values.localIp} readOnly aria-readonly="true" title="默认 127.0.0.1" onChange={(event) => update('localIp', event.target.value)} />
              <small>默认 127.0.0.1</small>
            </Field>
            <Field label="监听端口 *">
              <Input value={values.localPort} required inputMode="numeric" onChange={(event) => update('localPort', event.target.value)} placeholder="如 9191" />
              <small>范围 1-65535</small>
            </Field>
            <Field label="访问页">
              <Input value={values.accessPage} onChange={(event) => update('accessPage', event.target.value)} placeholder="如 /portal/login.html" />
              <small>可选访问入口</small>
            </Field>
            <Field label="默认地址（兜底） *">
              <Input value={values.targetUrl} required onChange={(event) => update('targetUrl', event.target.value)} placeholder="如 192.168.1.100:8080" />
              <small>未命中前缀时转发到此地址</small>
            </Field>
            <Field label="代理地址" className="md:col-span-2">
              <Input value={values.accessPageBaseUrl} onChange={(event) => update('accessPageBaseUrl', event.target.value)} placeholder="如 127.0.0.1:9999" />
              <small>命中路径前缀时转发到此地址</small>
            </Field>
          </div>
          <Field label="路径前缀">
            <div className="rounded-3xl border-[3px] border-clay-border bg-white p-3 shadow-clay-sm">
              <div className="flex min-h-10 flex-wrap gap-2">
                {values.pathPrefixes.length === 0 ? <Badge variant="yellow">未配置路径前缀，请求走默认地址</Badge> : values.pathPrefixes.map((prefix) => (
                  <Badge key={prefix} variant="indigo" className="gap-2">
                    {prefix}
                    <button type="button" className="rounded-full border-[2px] border-clay-border bg-clay-pink p-0.5" onClick={() => update('pathPrefixes', values.pathPrefixes.filter((item) => item !== prefix))} aria-label={`移除 ${prefix}`}>
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))}
              </div>
              <div className="mt-3 flex flex-col gap-2 sm:flex-row">
                <Input value={prefixInput} onChange={(event) => setPrefixInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addPrefix(); } }} placeholder="如 /api/users" />
                <Button type="button" variant="primary" onClick={addPrefix}><Plus className="h-4 w-4" />添加路径</Button>
              </div>
            </div>
            <small>访问监听地址时，命中前缀走代理地址，否则走默认地址</small>
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" variant="orange" disabled={saving}>{saving ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function Field({ label, className, children }: { label: string; className?: string; children: React.ReactNode }) {
  return (
    <label className={className ? `grid gap-1 text-sm font-black text-clay-ink ${className}` : 'grid gap-1 text-sm font-black text-clay-ink'}>
      <span>{label}</span>
      {children}
    </label>
  );
}
