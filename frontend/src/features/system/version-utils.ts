import type { AppVersionInfo, AutoUpdateStatus, ReleaseHistory, ReleaseHistoryItem, UpdateCheckResult } from './types';

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

/** 发布契约里的五个平台标识 → 展示用的系统名。 */
const PLATFORM_LABELS: Record<string, string> = {
  'macos-arm64': 'macOS',
  'macos-x64': 'macOS',
  'linux-x64': 'Linux',
  'linux-arm64': 'Linux',
  'windows-x64': 'Windows',
};

const PLATFORM_FAMILIES: Record<string, string> = {
  macos: 'macOS',
  darwin: 'macOS',
  linux: 'Linux',
  windows: 'Windows',
};

/**
 * 平台标签：只展示**系统名**（macOS / Linux / Windows）。
 *
 * <p>不展示 CPU 架构：界面上「macOS · Apple Silicon」这类文案过长，
 * 在版本抽屉与侧栏都会挤占空间，而架构对使用者没有决策价值——
 * 更新包由后端按 `platform` 标识自动选，不需要用户判断。
 * 架构仍可通过 `GET /admin/api/version` 的 `platform` 字段（如 `macos-arm64`）取得。</p>
 *
 * <p>只有后端确实没上报平台时才回落到「未知平台」；认不出的标识原样显示，不吞掉信息。</p>
 */
export function platformLabel(platform: string): string {
  const token = text(platform).toLowerCase();
  if (!token || token === 'unknown') {
    return '未知平台';
  }
  const known = PLATFORM_LABELS[token];
  if (known) {
    return known;
  }
  // 契约外的 `<family>-<arch>` 组合按前缀识别系统名
  const separator = token.indexOf('-');
  if (separator > 0) {
    const family = PLATFORM_FAMILIES[token.slice(0, separator)];
    if (family) {
      return family;
    }
  }
  return token;
}

/**
 * 归一化历史版本响应：后端不可用或字段缺失时给出安全默认，界面不空白。
 * 后端已按版本号倒序返回，这里不重排，只做防御性过滤。
 */
export function normalizeReleaseHistory(
  raw: Partial<ReleaseHistory> | null | undefined,
  fallbackVersion: string,
): ReleaseHistory {
  const releases = Array.isArray(raw?.releases)
    ? raw!.releases.filter((item): item is ReleaseHistoryItem => Boolean(item && text(item.version)))
    : [];
  return {
    currentVersion: text(raw?.currentVersion) || fallbackVersion,
    repository: text(raw?.repository),
    releases,
    message: text(raw?.message),
  };
}

/**
 * 历史版本列表的说明文案：有数据时说明条数与来源，无数据时说明原因。
 *
 * <p>注意「有数据但带 message」这一种：那是后端刷新失败、回落到缓存数据时
 * 给出的提示（形如「读取最新版本失败，以下为缓存数据：…」）。
 * 此时**必须把提示显示出来**，否则用户会以为看到的就是最新列表。</p>
 */
export function historySummary(history: ReleaseHistory | null): string {
  if (!history) {
    return '正在读取历史版本…';
  }
  if (history.releases.length === 0) {
    return history.message || '暂无可用的历史版本';
  }
  const suffix = history.repository ? ' · ' + history.repository : '';
  const summary = '共 ' + history.releases.length + ' 个已发布版本' + suffix;
  // 有数据时若仍带 message，说明是缓存回落，必须如实标注
  return history.message ? summary + ' · ' + history.message : summary;
}

/** 版本发布时间的紧凑格式（YYYY-MM-DD）；空值或非法值返回占位符。 */
export function formatReleaseDate(publishedAt: string): string {
  const value = text(publishedAt);
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (input: number) => String(input).padStart(2, '0');
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}

/** 修订记录预览：折叠态只显示首行，空内容给出占位文案。 */
export function releaseNotesPreview(notes: string, maxLength = 80): string {
  const value = text(notes);
  if (!value) {
    return '该版本未提供修订记录';
  }
  const firstLine = value.split(/\r?\n/).find((line) => text(line)) || value;
  const cleaned = cleanMarkdownLine(firstLine);
  return cleaned.length <= maxLength ? cleaned : cleaned.slice(0, maxLength) + '…';
}

/**
 * 把 GitHub Release 的 Markdown 说明转成纯文本展示。
 *
 * <p>抽屉里没有 Markdown 渲染器，直接展示会露出 `##`、`**`、表格竖线等标记。
 * 这里只做**展示层**的轻量降噪：保留内容与换行，去掉标记符号，
 * 表格行转成 ` · ` 分隔的一行，不引入任何依赖，也不改变原始数据。</p>
 */
export function formatReleaseNotes(notes: string): string {
  const value = notes || '';
  if (!text(value)) {
    return '该版本未提供修订记录';
  }
  const lines = value.split(/\r?\n/);
  const output: string[] = [];
  let inFence = false;
  for (const line of lines) {
    const trimmed = line.trim();
    // 代码块围栏本身没有信息量，但块内内容要原样保留
    if (/^```/.test(trimmed)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) {
      output.push(line);
      continue;
    }
    // 表格分隔行（| --- | --- |）纯粹是排版标记
    if (/^\|?[\s:|-]+\|?$/.test(trimmed) && trimmed.includes('-')) {
      continue;
    }
    if (trimmed.startsWith('|')) {
      const cells = trimmed.split('|').map((cell) => cleanMarkdownLine(cell)).filter((cell) => cell !== '');
      if (cells.length > 0) {
        output.push(cells.join(' · '));
      }
      continue;
    }
    output.push(cleanMarkdownLine(line));
  }
  return output.join('\n').replace(/\n{3,}/g, '\n\n').trim();
}

/** 去掉单行里的 Markdown 标记，保留可见文字。 */
function cleanMarkdownLine(line: string): string {
  let result = line.replace(/\s+$/, '');
  // 引用块
  result = result.replace(/^\s*>\s?/, '');
  // 标题
  result = result.replace(/^\s*#{1,6}\s*/, '');
  // 列表项
  result = result.replace(/^(\s*)[-*+]\s+/, '$1• ');
  // 图片与链接：保留可读文字
  result = result.replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1');
  result = result.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1');
  // 粗体 / 斜体 / 行内代码 / 删除线
  result = result.replace(/\*\*([^*]+)\*\*/g, '$1');
  result = result.replace(/__([^_]+)__/g, '$1');
  result = result.replace(/`([^`]*)`/g, '$1');
  result = result.replace(/~~([^~]+)~~/g, '$1');
  return result.trim();
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
