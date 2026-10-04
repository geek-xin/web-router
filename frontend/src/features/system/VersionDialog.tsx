import * as React from 'react';
import { CheckCircle2, Download, Info, RefreshCw, TriangleAlert, X } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { fetchJson, jsonRequest } from '@/lib/api';
import type { AppVersionInfo, AutoUpdateStatus, UpdateApplyResult, UpdateCheckResult } from './types';
import {
  applyDisabledReason,
  autoUpdateIdleText,
  autoUpdateSummary,
  canApplyUpdate,
  fallbackVersionInfo,
  formatBuildTime,
  formatBytes,
  installModeLabel,
  normalizeVersionInfo,
  platformLabel,
  updateChannelLabel,
  updateStatusText,
} from './version-utils';

interface VersionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 由 App 提供，便于在接口不可用时仍展示已知版本。 */
  versionFallback: string;
  repositoryFallback: string;
}

const RELEASE_URL_LABEL = '查看 Release 说明';

export function VersionDialog({ open, onOpenChange, versionFallback, repositoryFallback }: VersionDialogProps) {
  const [info, setInfo] = React.useState<AppVersionInfo | null>(null);
  const [check, setCheck] = React.useState<UpdateCheckResult | null>(null);
  const [checking, setChecking] = React.useState(false);
  const [applying, setApplying] = React.useState(false);
  const [error, setError] = React.useState('');
  const [autoStatus, setAutoStatus] = React.useState<AutoUpdateStatus | null>(null);

  const loadVersion = React.useCallback(async () => {
    try {
      const raw = await fetchJson<Partial<AppVersionInfo>>('/admin/api/version');
      setInfo(normalizeVersionInfo(raw, fallbackVersionInfo(versionFallback, repositoryFallback)));
      setError('');
    } catch (loadError) {
      setInfo(fallbackVersionInfo(versionFallback, repositoryFallback));
      setError(loadError instanceof Error ? loadError.message : '版本信息读取失败');
    }
  }, [repositoryFallback, versionFallback]);

  const checkUpdate = React.useCallback(async (silent = false) => {
    setChecking(true);
    try {
      const result = await fetchJson<UpdateCheckResult>('/admin/api/update/check');
      setCheck(result);
      if (!silent) {
        toast.success(result.updateAvailable ? '发现新版本 ' + result.latestVersion : '当前已是最新版本');
      }
    } catch (checkError) {
      const message = checkError instanceof Error ? checkError.message : '检查更新失败';
      setCheck(null);
      if (!silent) {
        toast.error(message);
      }
    } finally {
      setChecking(false);
    }
  }, []);

  const loadAutoStatus = React.useCallback(async () => {
    try {
      setAutoStatus(await fetchJson<AutoUpdateStatus>('/admin/api/update/auto'));
    } catch {
      // 旧后端没有该接口时保持 null，界面降级为不展示自动更新状态
      setAutoStatus(null);
    }
  }, []);

  React.useEffect(() => {
    if (!open) {
      return;
    }
    setCheck(null);
    void loadVersion();
    void checkUpdate(true);
    void loadAutoStatus();
  }, [open, loadVersion, checkUpdate, loadAutoStatus]);

  // 打开期间轮询自动更新状态：在途请求数与空闲判定会实时变化
  React.useEffect(() => {
    if (!open) {
      return;
    }
    const timer = window.setInterval(() => void loadAutoStatus(), 3000);
    return () => window.clearInterval(timer);
  }, [open, loadAutoStatus]);

  async function applyUpdate() {
    if (!check) {
      return;
    }
    setApplying(true);
    try {
      const result = await fetchJson<UpdateApplyResult>('/admin/api/update/apply', jsonRequest({ version: check.latestVersion }));
      toast.success('已下载 ' + result.version + '，应用即将重启完成更新');
      setError('');
    } catch (applyError) {
      toast.error(applyError instanceof Error ? applyError.message : '更新失败');
    } finally {
      setApplying(false);
    }
  }

  if (!open) {
    return null;
  }

  const current = info || fallbackVersionInfo(versionFallback, repositoryFallback);
  const disabledReason = applyDisabledReason(check, current);

  return (
    <>
      <button className="drawer-scrim" type="button" aria-label="关闭版本信息" onClick={() => onOpenChange(false)} />
      <aside className="drawer scroll-area" role="dialog" aria-modal="true" aria-labelledby="version-dialog-title">
        <header className="drawer-head">
          <div className="drawer-title-row">
            <div className="min-w-0 flex-1">
              <h2 id="version-dialog-title" className="drawer-title">版本与更新</h2>
              <span className="route-card-status mt-1.5">
                <span className="dot state-running" aria-hidden="true" />
                <span className="font-mono">v{current.version}</span>
              </span>
            </div>
            <button type="button" className="icon-button" onClick={() => onOpenChange(false)} aria-label="关闭版本信息">
              <X className="h-4 w-4" />
            </button>
          </div>
        </header>

        <div className="drawer-body scroll-area">
          {error && <div className="alert-error mb-3" role="alert">{error}</div>}

          <section className="section">
            <div className="section-head">
              <Info className="h-3.5 w-3.5" aria-hidden="true" />
              <h3 className="section-title">当前安装</h3>
            </div>
            <div className="field-grid">
              <DrawerField label="版本号" value={'v' + current.version} />
              <DrawerField label="构建时间" value={formatBuildTime(current.buildTime)} />
              <DrawerField label="运行平台" value={platformLabel(current.platform)} />
              <DrawerField label="安装方式" value={installModeLabel(current.installMode)} />
              <DrawerField label="更新通道" value={updateChannelLabel(current.updateChannel)} />
              <DrawerField label="自动更新" value={current.updateSupported ? '支持' : '不支持'} />
            </div>
          </section>

          <section className="section">
            <div className="section-head">
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              <h3 className="section-title">自动更新</h3>
            </div>
            <p className="text-[12.5px]" style={{ color: 'var(--surface-ink-muted)' }}>
              {autoUpdateSummary(autoStatus, current.updateSupported)}
            </p>
            {autoStatus?.autoEnabled && (
              <div className="field-grid">
                <DrawerField label="在途请求" value={String(autoStatus.inFlightCount)} />
                <DrawerField label="空闲判定" value={autoStatus.idle ? '空闲' : '忙碌'} />
                <DrawerField label="待应用版本" value={autoStatus.pendingVersion ? 'v' + autoStatus.pendingVersion : '—'} />
                <DrawerField label="静默阈值" value={autoStatus.quietSeconds + ' 秒'} />
              </div>
            )}
            {autoUpdateIdleText(autoStatus) && (
              <p className="text-[11.5px]" style={{ color: 'var(--surface-ink-subtle)' }}>
                {autoUpdateIdleText(autoStatus)}
              </p>
            )}
          </section>

          <section className="section">
            <div className="section-head">
              <RefreshCw className="h-3.5 w-3.5" aria-hidden="true" />
              <h3 className="section-title">手动更新</h3>
              <Button size="sm" variant="outline" onClick={() => void checkUpdate(false)} disabled={checking}>
                <RefreshCw className={checking ? 'h-3.5 w-3.5 animate-spin' : 'h-3.5 w-3.5'} aria-hidden="true" />
                {checking ? '检查中' : '检查更新'}
              </Button>
            </div>

            <p className="text-[12.5px]" style={{ color: 'var(--surface-ink-muted)' }} aria-live="polite">{updateStatusText(check, checking)}</p>

            {check?.updateAvailable && (
              <>
                <div className="metric-grid">
                  <DrawerMetric label="最新版本" value={'v' + check.latestVersion} />
                  <DrawerMetric label="资产大小" value={formatBytes(check.assetSize)} />
                  <DrawerMetric label="通道" value={check.prerelease ? '预览' : '稳定'} />
                </div>
                {check.assetName && <DrawerField label="下载资产" value={check.assetName} />}
                {check.releaseNotes && (
                  <div>
                    <span className="route-field-label">更新说明</span>
                    <pre
                      className="scroll-area mt-1 max-h-48 overflow-auto whitespace-pre-wrap p-3 text-[12px] leading-5"
                      style={{ border: '1px solid var(--surface-line)', borderRadius: 'var(--radius-card)', background: 'var(--surface-sunken)', color: 'var(--surface-ink-muted)', margin: 0 }}
                    >{check.releaseNotes}</pre>
                  </div>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button variant="primary" onClick={() => void applyUpdate()} disabled={applying || Boolean(disabledReason)} title={disabledReason || '下载并安装更新'}>
                    <Download className="h-3.5 w-3.5" aria-hidden="true" />
                    {applying ? '正在下载…' : '一键更新'}
                  </Button>
                  {check.releaseUrl && (
                    <Button variant="outline" asChild>
                      <a href={check.releaseUrl} target="_blank" rel="noreferrer">{RELEASE_URL_LABEL}</a>
                    </Button>
                  )}
                </div>
                {disabledReason && (
                  <p className="flex items-start gap-1.5 text-[11.5px]" style={{ color: 'var(--surface-ink-subtle)' }}>
                    <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    {disabledReason}
                  </p>
                )}
                <p className="text-[11.5px]" style={{ color: 'var(--surface-ink-subtle)' }}>
                  更新会下载新版本到安装目录的 updates/ 子目录并校验校验和，随后应用自动退出，由更新脚本完成替换与重启。
                </p>
              </>
            )}

            {check && !check.updateAvailable && (
              <p className="flex items-center gap-1.5 text-[12px]" style={{ color: 'var(--surface-success)' }}>
                <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                已是最新版本
              </p>
            )}
          </section>
        </div>
      </aside>
    </>
  );
}

function DrawerField({ label, value }: { label: string; value: string }) {
  return (
    <div className="field">
      <span className="route-field-label">{label}</span>
      <span className="field-value" title={value}>{value}</span>
    </div>
  );
}

function DrawerMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="field">
      <span className="route-field-label">{label}</span>
      <span className="metric-figure">{value}</span>
    </div>
  );
}
