import * as React from 'react';
import { Plus, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import type { RouteConfig, RouteConfigPayload, RouteFormValues } from './types';
import { nextCopyName, normalizePathPrefix, routeToFormValues, validateRoutePayload } from './route-utils';

export interface RouteFormDialogProps {
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
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={mode === 'copy' ? 'w-[min(94vw,640px)]' : 'w-[min(96vw,820px)]'}>
        <DialogHeader>
          <DialogTitle>{mode === 'edit' ? '编辑路由' : mode === 'copy' ? '拷贝路由' : '新增路由'}</DialogTitle>
          <DialogDescription>访问监听地址时，命中前缀走代理地址，否则走默认地址。</DialogDescription>
        </DialogHeader>
        <RouteFormPanel
          active={open}
          mode={mode}
          route={route}
          existingNames={existingNames}
          existingBindings={existingBindings}
          onSubmit={onSubmit}
          onCancel={() => onOpenChange(false)}
          closeAfterSubmit
        />
      </DialogContent>
    </Dialog>
  );
}

export interface RouteFormPanelProps {
  active?: boolean;
  mode: 'create' | 'edit' | 'copy';
  route?: RouteConfig | null;
  existingNames: string[];
  existingBindings: string[];
  onSubmit: (payload: RouteConfigPayload, mode: 'create' | 'edit' | 'copy', route?: RouteConfig | null) => Promise<void>;
  onCancel?: () => void;
  closeAfterSubmit?: boolean;
  submitLabel?: string;
  cancelLabel?: string;
  onValuesChange?: (values: RouteFormValues) => void;
  formId?: string;
  showActions?: boolean;
}

export function RouteFormPanel({ active = true, mode, route, existingNames, existingBindings, onSubmit, onCancel, closeAfterSubmit = false, submitLabel, cancelLabel = '取消', onValuesChange, formId, showActions = true }: RouteFormPanelProps) {
  const [values, setValues] = React.useState<RouteFormValues>(emptyValues);
  const [prefixInput, setPrefixInput] = React.useState('');
  const [errors, setErrors] = React.useState<string[]>([]);
  const [saving, setSaving] = React.useState(false);

  React.useEffect(() => {
    if (!active) {
      return;
    }
    const nextValues = mode === 'create' || !route
      ? emptyValues
      : (() => {
          const next = routeToFormValues(route);
          return { ...next, name: mode === 'copy' ? nextCopyName(next.name, existingNames) : next.name, enabled: mode === 'copy' ? false : next.enabled };
        })();
    setValues(nextValues);
    onValuesChange?.(nextValues);
    setPrefixInput('');
    setErrors([]);
  }, [active, mode, route, existingNames, onValuesChange]);

  function update<K extends keyof RouteFormValues>(key: K, value: RouteFormValues[K]) {
    setValues((current) => {
      const next = { ...current, [key]: value };
      onValuesChange?.(next);
      return next;
    });
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
      if (closeAfterSubmit) {
        onCancel?.();
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <form id={formId} className="grid gap-4" onSubmit={handleSubmit}>
      {errors.length > 0 && (
        <div className="console-error-box" role="alert">
          {errors.map((error) => <div key={error}>{error}</div>)}
        </div>
      )}

      <div className="console-form-grid">
        <Field label="路由名称" required className="sm:col-span-2">
          <Input value={values.name} onChange={(event) => update('name', event.target.value)} maxLength={50} placeholder="如 用户服务 / API-测试" />
          <small className="console-field-hint">最多 50 个字；配置文件名会自动生成</small>
        </Field>
        <Field label="监听 IP">
          <Input value={values.localIp} readOnly aria-readonly="true" title="默认 127.0.0.1" onChange={(event) => update('localIp', event.target.value)} />
          <small className="console-field-hint">默认 127.0.0.1</small>
        </Field>
        <Field label="监听端口" required>
          <Input value={values.localPort} required inputMode="numeric" onChange={(event) => update('localPort', event.target.value)} placeholder="如 18081" />
          <small className="console-field-hint">范围 1-65535</small>
        </Field>
        <Field label="访问页">
          <Input value={values.accessPage} onChange={(event) => update('accessPage', event.target.value)} placeholder="如 /portal/login.html" />
          <small className="console-field-hint">可选访问入口</small>
        </Field>
        <Field label="默认地址（兜底）" required>
          <Input value={values.targetUrl} required onChange={(event) => update('targetUrl', event.target.value)} placeholder="如 127.0.0.1:8081" />
          <small className="console-field-hint">未命中前缀时转发到此地址</small>
        </Field>
        <Field label="代理地址" className="sm:col-span-2">
          <Input value={values.accessPageBaseUrl} onChange={(event) => update('accessPageBaseUrl', event.target.value)} placeholder="如 127.0.0.1:9999" />
          <small className="console-field-hint">命中路径前缀时转发到此地址</small>
        </Field>
      </div>

      <Field label="路径前缀">
        <div className="prefix-editor">
          <div className="flex min-h-7 flex-wrap gap-1.5">
            {values.pathPrefixes.length === 0 ? (
              <span className="console-badge console-badge-muted">未配置路径前缀，请求走默认地址</span>
            ) : (
              values.pathPrefixes.map((prefix) => (
                <span key={prefix} className="route-prefix-chip gap-1.5">
                  {prefix}
                  <button type="button" className="prefix-chip-remove" onClick={() => update('pathPrefixes', values.pathPrefixes.filter((item) => item !== prefix))} aria-label={`移除 ${prefix}`}>
                    <X className="h-2.5 w-2.5" />
                  </button>
                </span>
              ))
            )}
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <Input
              value={prefixInput}
              onChange={(event) => setPrefixInput(event.target.value)}
              onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); addPrefix(); } }}
              placeholder="如 /api/users"
            />
            <Button type="button" variant="outline" onClick={addPrefix}><Plus className="h-3.5 w-3.5" />添加路径</Button>
          </div>
        </div>
        <small className="console-field-hint">访问监听地址时，命中前缀走代理地址，否则走默认地址</small>
      </Field>

      {showActions && (
        <DialogFooter>
          {onCancel && <Button type="button" variant="outline" onClick={onCancel}>{cancelLabel}</Button>}
          <Button type="submit" variant="primary" disabled={saving}>{saving ? '保存中…' : submitLabel || '保存'}</Button>
        </DialogFooter>
      )}
    </form>
  );
}

function Field({ label, required = false, className, children }: { label: string; required?: boolean; className?: string; children: React.ReactNode }) {
  return (
    <label className={className ? `console-field ${className}` : 'console-field'}>
      <span className="console-field-label">{label}{required && <span className="required-field-mark" aria-label="必填">*</span>}</span>
      {children}
    </label>
  );
}
