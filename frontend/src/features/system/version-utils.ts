import type { AppVersionInfo, AutoUpdateStatus, UpdateCheckResult } from './types';

/** 后端不可用时的兜底版本信息，保证界面不空白。 */
export function fallbackVersionInfo(version: string, repository: string): AppVersionInfo {
  return {
    version,
    buildTime: '',
    platform: 'unknown',
    installMode: 'jar',
    updateSupported: false,
    updateChannel: 'stable',
    repository,
  };
}

export function normalizeVersionInfo(raw: Partial<AppVersionInfo> | null | undefined, fallback: AppVersionInfo): AppVersionInfo {
  return {
    version: text(raw?.version) || fallback.version,
    buildTime: text(raw?.buildTime),
    platform: text(raw?.platform) || fallback.platform,
    installMode: text(raw?.installMode) || fallback.installMode,
    updateSupported: raw?.updateSupported === true,
    updateChannel: text(raw?.updateChannel) || fallback.updateChannel,
    repository: text(raw?.repository) || fallback.repository,
  };
}

/** 自动更新策略的一句话说明，展示在版本抽屉里。 */
export function autoUpdateSummary(status: AutoUpdateStatus | null, supported: boolean): string {
  if (!supported) {
    return '当前安装形态不支持自动更新，请手动下载新版本';
  }
  if (!status) {
    return '正在读取自动更新状态…';
  }
  if (!status.autoEnabled) {
    return '自动更新已关闭，可手动检查并安装';
  }
  if (status.pendingVersion) {
    return status.idle
      ? '新版本 ' + status.pendingVersion + ' 已就绪，正在空闲窗口应用'
      : '新版本 ' + status.pendingVersion + ' 已下载，将在没有代理请求时自动应用';
  }
  return status.autoApply
    ? '后台自动检查并下载，空闲时无感完成更新'
    : '后台自动检查并下载，安装前需要你确认';
}

/** 空闲窗口说明：让用户理解「为什么现在还没重启」。 */
export function autoUpdateIdleText(status: AutoUpdateStatus | null): string {
  if (!status || !status.autoEnabled) {
    return '';
  }
  if (status.inFlightCount > 0) {
    return '当前有 ' + status.inFlightCount + ' 个代理请求进行中，等待处理完成后更新';
  }
  const quiet = Math.max(0, status.quietSeconds);
  if (quiet > 0 && status.idleMillis < quiet * 1000) {
    return '等待静默 ' + quiet + ' 秒（当前 ' + Math.round(status.idleMillis / 1000) + ' 秒）';
  }
  return '当前无代理请求，可安全更新';
}

export function platformLabel(platform: string): string {
  switch (platform) {
    case 'macos-arm64':
      return 'macOS · Apple Silicon';
    case 'macos-x64':
      return 'macOS · Intel';
    case 'linux-x64':
      return 'Linux · x64';
    case 'linux-arm64':
      return 'Linux · ARM64';
    case 'windows-x64':
      return 'Windows · x64';
    default:
      return '未识别平台';
  }
}

export function installModeLabel(installMode: string): string {
  switch (installMode) {
    case 'app-image':
      return '免安装程序';
    case 'jar':
      return 'JAR 服务';
    default:
      return installMode || '未知';
  }
}

export function updateChannelLabel(channel: string): string {
  return channel === 'prerelease' ? '预览通道' : '稳定通道';
}

/** 构建时间显示为本地时间；空值显示占位符。 */
export function formatBuildTime(buildTime: string): string {
  const value = text(buildTime);
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (input: number) => String(input).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formatBytes(bytes?: number | null): string {
  const value = Number(bytes ?? 0);
  if (!Number.isFinite(value) || value <= 0) {
    return '—';
  }
  if (value < 1024) {
    return value + ' B';
  }
  const units = ['KB', 'MB', 'GB'];
  let size = value / 1024;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return (size >= 100 ? Math.round(size).toString() : size.toFixed(1).replace(/\.0$/, '')) + ' ' + units[unitIndex];
}

/** 校验结果摘要：无更新 / 有更新 / 无法检查。 */
export function updateStatusText(result: UpdateCheckResult | null, checking: boolean): string {
  if (checking) {
    return '正在检查更新…';
  }
  if (!result) {
    return '尚未检查更新';
  }
  if (result.updateAvailable) {
    return `发现新版本 ${result.latestVersion}`;
  }
  return result.message || '当前已是最新版本';
}

/** 只有「有更新 + 有可下载资产 + 当前安装可自更新」才允许一键更新。 */
export function canApplyUpdate(result: UpdateCheckResult | null, info: AppVersionInfo | null): boolean {
  return Boolean(result?.updateAvailable && result.assetUrl && info?.updateSupported);
}

export function applyDisabledReason(result: UpdateCheckResult | null, info: AppVersionInfo | null): string {
  if (!result?.updateAvailable) {
    return '没有可用更新';
  }
  if (!result.assetUrl) {
    return '该版本没有匹配当前安装方式的可下载资产';
  }
  if (!info?.updateSupported) {
    return '当前安装方式不支持自动更新，请手动下载安装包';
  }
  return '';
}

function text(value?: string | null): string {
  return (value || '').trim();
}
