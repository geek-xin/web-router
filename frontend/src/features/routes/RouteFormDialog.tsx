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
      <DialogContent className={mode === 'copy' ? 'w-[min(94vw,640px)]' : 'w-[min(96vw,800px)]'}>
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

  /**
   * 用路由 id + 模式标识"该重置了"，而不是依赖数组引用。
   *
   * <p>父组件每次渲染都会新建 existingNames / existingBindings 数组（例如每 5 秒的
   * 指标轮询），把它们放进依赖会让表单在用户输入过程中被反复重置——
   * 表现为刚填好的端口过几秒自己变回旧值。这里只在真正切换路由/模式时重置。</p>
   */
  const resetKey = React.useMemo(
    () => [active ? 'active' : 'idle', mode, route?.id || ''].join('|'),
    [active, mode, route?.id],
  );
  const latestRef = React.useRef({ route, existingNames, onValuesChange });
  latestRef.current = { route, existingNames, onValuesChange };

  React.useEffect(() => {
    if (!active) {
      return;
    }
    const { route: currentRoute, existingNames: names, onValuesChange: notify } = latestRef.current;
    const nextValues = mode === 'create' || !currentRoute
      ? emptyValues
      : (() => {
          const next = routeToFormValues(currentRoute);
          return { ...next, name: mode === 'copy' ? nextCopyName(next.name, names) : next.name, enabled: mode === 'copy' ? false : next.enabled };
        })();
    setValues(nextValues);
    notify?.(nextValues);
    setPrefixInput('');
    setErrors([]);
    // 仅在 resetKey 变化时重置；其余 props 通过 latestRef 读取最新值
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetKey]);

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
    const bindingExcludes = mode === 'edit' && route ? existingBindings.filter((binding) => binding !== (route.localIp || '127.0.0.1') + ':' + (route.localPort || '')) : existingBindings;
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
    } catch (submitError) {
      // 保存失败必须回显在表单里：否则 Promise 变成未处理的 rejection，
      // 用户只看到按钮闪一下，完全不知道失败了（例如端口被占用）。
      setErrors([submitError instanceof Error ? submitError.message : '保存失败，请重试']);
    } finally {
      setSaving(false);
    }
  }

  return (
    <form id={formId} className="grid gap-4" onSubmit={handleSubmit}>
      {errors.length > 0 && (
        <div className="alert-error" role="alert">
          {errors.map((error) => <div key={error}>{error}</div>)}
        </div>
      )}

      <div className="form-grid">
        <Field label="路由名称" required className="sm:col-span-2">
          <Input value={values.name} onChange={(event) => update('name', event.target.value)} maxLength={50} placeholder="如 用户服务 / API-测试" />
          <small className="form-hint">最多 50 个字；配置文件名会自动生成</small>
        </Field>
        <Field label="监听 IP">
          <Input value={values.localIp} readOnly aria-readonly="true" title="默认 127.0.0.1" onChange={(event) => update('localIp', event.target.value)} />
          <small className="form-hint">默认 127.0.0.1</small>
        </Field>
        <Field label="监听端口" required>
          <Input value={values.localPort} required inputMode="numeric" onChange={(event) => update('localPort', event.target.value)} placeholder="如 18081" />
          <small className="form-hint">范围 1-65535</small>
        </Field>
        <Field label="访问页">
          <Input value={values.accessPage} onChange={(event) => update('accessPage', event.target.value)} placeholder="如 /portal/login.html" />
          <small className="form-hint">可选访问入口</small>
        </Field>
        <Field label="默认地址（兜底）" required>
          <Input value={values.targetUrl} required onChange={(event) => update('targetUrl', event.target.value)} placeholder="如 127.0.0.1:8081" />
          <small className="form-hint">未命中前缀时转发到此地址</small>
        </Field>
        <Field label="代理地址" className="sm:col-span-2">
          <Input value={values.accessPageBaseUrl} onChange={(event) => update('accessPageBaseUrl', event.target.value)} placeholder="如 127.0.0.1:9999" />
          <small className="form-hint">命中路径前缀时转发到此地址</small>
        </Field>
      </div>

      <Field label="路径前缀">
        <div className="prefix-editor">
          <div className="flex min-h-6 flex-wrap gap-1.5">
            {values.pathPrefixes.length === 0 ? (
              <span className="chip">未配置路径前缀，请求走默认地址</span>
            ) : (
              values.pathPrefixes.map((prefix) => (
                <span key={prefix} className="route-prefix-chip gap-1.5">
                  {prefix}
                  <button type="button" className="prefix-remove" onClick={() => update('pathPrefixes', values.pathPrefixes.filter((item) => item !== prefix))} aria-label={'移除 ' + prefix}>
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
        <small className="form-hint">访问监听地址时，命中前缀走代理地址，否则走默认地址</small>
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
    <label className={className ? 'form-field ' + className : 'form-field'}>
      <span className="form-label">{label}{required && <span className="required-mark" aria-label="必填">*</span>}</span>
      {children}
    </label>
  );
}
