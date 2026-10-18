import { describe, expect, it } from 'vitest';
import {
  applyDisabledReason,
  autoUpdateIdleText,
  autoUpdateSummary,
  canApplyUpdate,
  fallbackVersionInfo,
  formatBuildTime,
  formatBytes,
  formatReleaseDate,
  formatReleaseNotes,
  historySummary,
  installModeLabel,
  normalizeReleaseHistory,
  normalizeVersionInfo,
  platformLabel,
  releaseNotesPreview,
  updateChannelLabel,
  updateStatusText,
} from './version-utils';
import type { AppVersionInfo, AutoUpdateStatus, ReleaseHistory, UpdateCheckResult } from './types';

function autoStatus(overrides: Partial<AutoUpdateStatus> = {}): AutoUpdateStatus {
  return {
    autoEnabled: true,
    autoApply: true,
    quietSeconds: 30,
    inFlightCount: 0,
    idleMillis: 60_000,
    idle: true,
    pendingVersion: '',
    pendingAsset: '',
    ...overrides,
  };
}

const info: AppVersionInfo = {
  version: '1.3.0',
  buildTime: '2026-09-30T15:56:37Z',
  platform: 'macos-arm64',
  installMode: 'app-image',
  updateSupported: true,
  updateChannel: 'stable',
  repository: 'geek-xin/web-router',
};

const available: UpdateCheckResult = {
  currentVersion: '1.3.0',
  latestVersion: '1.4.0',
  updateAvailable: true,
  prerelease: false,
  releaseName: 'v1.4.0',
  releaseNotes: 'notes',
  publishedAt: '2026-10-01T00:00:00Z',
  releaseUrl: 'https://example.com/release',
  assetName: 'wrouter-1.4.0-macos-arm64-app.zip',
  assetUrl: 'https://example.com/asset.zip',
  assetSize: 52_428_800,
  assetDigest: 'sha256:abc',
  message: '发现新版本 1.4.0',
};

describe('auto update status copy', () => {
  it('explains the seamless flow when auto update is enabled', () => {
    expect(autoUpdateSummary(autoStatus(), true)).toContain('无感');
    expect(autoUpdateSummary(null, true)).toContain('正在读取');
    expect(autoUpdateSummary(autoStatus(), false)).toContain('不支持');
  });

  it('explains why the update has not been applied yet', () => {
    expect(autoUpdateIdleText(autoStatus({ inFlightCount: 3, idle: false }))).toContain('3 个代理请求');
    expect(autoUpdateIdleText(autoStatus({ idle: false, idleMillis: 5_000 }))).toContain('等待静默 30 秒');
    expect(autoUpdateIdleText(autoStatus({ idle: true }))).toContain('可安全更新');
    expect(autoUpdateIdleText(null)).toBe('');
  });

  it('describes a staged update waiting for the idle window', () => {
    expect(autoUpdateSummary(autoStatus({ pendingVersion: '1.4.0', idle: false }), true)).toContain('1.4.0');
    expect(autoUpdateSummary(autoStatus({ pendingVersion: '1.4.0', idle: true }), true)).toContain('正在空闲窗口应用');
    expect(autoUpdateSummary(autoStatus({ autoEnabled: false }), true)).toContain('已关闭');
  });
});

describe('version-utils', () => {
  it('labels platforms and install modes in Chinese', () => {
    // 只展示系统名：架构对使用者没有决策价值，且会让文案过长
    expect(platformLabel('macos-arm64')).toBe('macOS');
    expect(platformLabel('macos-x64')).toBe('macOS');
    expect(platformLabel('linux-x64')).toBe('Linux');
    expect(platformLabel('linux-arm64')).toBe('Linux');
    expect(platformLabel('windows-x64')).toBe('Windows');
    expect(installModeLabel('jar')).toBe('JAR 服务');
    expect(installModeLabel('app-image')).toBe('免安装程序');
    expect(updateChannelLabel('prerelease')).toBe('预览通道');
    expect(updateChannelLabel('stable')).toBe('稳定通道');
  });

  it('shows only the OS name and never the CPU architecture', () => {
    // 同系统的不同架构必须折叠成同一个标签
    expect(platformLabel('macos-arm64')).toBe(platformLabel('macos-x64'));
    expect(platformLabel('linux-x64')).toBe(platformLabel('linux-arm64'));
    // 任何平台标签都不应包含架构后缀
    const platforms = ['macos-arm64', 'macos-x64', 'linux-x64', 'linux-arm64', 'windows-x64',
      'linux-riscv64', 'windows-arm64', 'macos-aarch64'];
    for (const token of platforms) {
      const label = platformLabel(token);
      expect(label).not.toContain('·');
      expect(label).not.toMatch(/arm64|x64|x86|Apple Silicon|Intel/i);
    }
  });

  it('degrades gracefully instead of showing an unrecognised platform', () => {
    // 契约外的组合按前缀识别系统名，不再一律「未识别平台」
    expect(platformLabel('linux-riscv64')).toBe('Linux');
    expect(platformLabel('windows-arm64')).toBe('Windows');
    expect(platformLabel('macos-aarch64')).toBe('macOS');
    // 认不出的标识原样显示，不吞掉后端上报的信息
    expect(platformLabel('solaris-sparc')).toBe('solaris-sparc');
    expect(platformLabel('freebsd')).toBe('freebsd');
    // 只有确实没有平台信息时才是「未知平台」
    expect(platformLabel('')).toBe('未知平台');
    expect(platformLabel('unknown')).toBe('未知平台');
    expect(platformLabel('UNKNOWN')).toBe('未知平台');
    expect(platformLabel('  ')).toBe('未知平台');
  });

  it('normalizes the release history and survives a missing payload', () => {
    const history = normalizeReleaseHistory(
      {
        currentVersion: '1.3.0',
        repository: 'geek-xin/web-router',
        releases: [
          { version: '1.4.0', name: 'v1.4.0', publishedAt: '2026-10-01T00:00:00Z', releaseUrl: 'u', prerelease: false, current: false, notes: '新增历史版本' },
          // 缺 version 的脏数据必须被过滤，否则渲染出空行
          { version: '', name: 'broken' } as never,
        ],
        message: '',
      },
      '1.3.0',
    );

    expect(history.releases).toHaveLength(1);
    expect(history.releases[0].version).toBe('1.4.0');
    expect(history.repository).toBe('geek-xin/web-router');

    const empty = normalizeReleaseHistory(null, '1.3.0');
    expect(empty.currentVersion).toBe('1.3.0');
    expect(empty.releases).toEqual([]);
    expect(empty.message).toBe('');
  });

  it('summarises the history for the loading, empty and populated states', () => {
    expect(historySummary(null)).toContain('正在读取');

    const empty: ReleaseHistory = { currentVersion: '1.3.0', repository: 'o/r', releases: [], message: '无法连接 GitHub' };
    expect(historySummary(empty)).toBe('无法连接 GitHub');

    const noMessage: ReleaseHistory = { currentVersion: '1.3.0', repository: '', releases: [], message: '' };
    expect(historySummary(noMessage)).toBe('暂无可用的历史版本');

    const populated = normalizeReleaseHistory(
      { currentVersion: '1.3.0', repository: 'o/r', releases: [{ version: '1.4.0', name: '', publishedAt: '', releaseUrl: '', prerelease: false, current: false, notes: '' }], message: '' },
      '1.3.0',
    );
    expect(historySummary(populated)).toBe('共 1 个已发布版本 · o/r');
  });

  /**
   * 有数据但带 message = 后端刷新失败、回落到缓存数据。
   * 这种提示必须显示出来，否则用户会以为看到的就是最新列表。
   */
  it('surfaces the stale-cache notice even when releases are present', () => {
    const stale = normalizeReleaseHistory(
      {
        currentVersion: '1.3.0',
        repository: 'o/r',
        releases: [{ version: '1.4.0', name: '', publishedAt: '', releaseUrl: '', prerelease: false, current: false, notes: '' }],
        message: '读取最新版本失败，以下为缓存数据：ConnectException',
      },
      '1.3.0',
    );

    const summary = historySummary(stale);

    expect(summary).toContain('共 1 个已发布版本');
    expect(summary).toContain('缓存数据');
  });

  it('strips markdown markers from release notes for plain-text display', () => {
    const notes = [
      '## wrouter 1.3.0',
      '',
      '### 下载 / Downloads',
      '',
      '| 方式 | 文件 |',
      '| --- | --- |',
      '| 免安装 · macOS | `wrouter-1.3.0-macos-arm64-app.zip` |',
      '',
      '- **新增** 历史版本视图',
      '- 见 [文档](https://example.test/docs)',
    ].join('\n');

    const formatted = formatReleaseNotes(notes);

    // 标记符号不该出现在展示文本里
    expect(formatted).not.toContain('##');
    expect(formatted).not.toContain('**');
    expect(formatted).not.toContain('|');
    // 内容本身必须保留
    expect(formatted).toContain('wrouter 1.3.0');
    expect(formatted).toContain('wrouter-1.3.0-macos-arm64-app.zip');
    expect(formatted).toContain('新增 历史版本视图');
    expect(formatted).toContain('文档');
    expect(formatted).not.toContain('https://example.test/docs');
    // 表格行折叠为一行
    expect(formatted).toContain('方式 · 文件');
  });

  it('keeps code fences intact and handles empty notes', () => {
    const withFence = formatReleaseNotes('说明\n```\n## 保留原文\n```\n结尾');
    expect(withFence).toContain('## 保留原文');

    expect(formatReleaseNotes('')).toBe('该版本未提供修订记录');
    expect(formatReleaseNotes('   ')).toBe('该版本未提供修订记录');
  });

  it('formats release dates and previews notes defensively', () => {
    expect(formatReleaseDate('2026-10-01T00:00:00Z')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(formatReleaseDate('')).toBe('—');
    expect(formatReleaseDate('not-a-date')).toBe('not-a-date');

    expect(releaseNotesPreview('## 新增历史版本\n- 其它')).toBe('新增历史版本');
    expect(releaseNotesPreview('')).toBe('该版本未提供修订记录');
    expect(releaseNotesPreview('   ')).toBe('该版本未提供修订记录');
    const long = releaseNotesPreview('a'.repeat(200), 20);
    expect(long).toHaveLength(21);
    expect(long.endsWith('…')).toBe(true);
  });

  it('falls back when the backend version endpoint is unavailable', () => {
    const fallback = fallbackVersionInfo('1.3.0', 'geek-xin/web-router');
    const normalized = normalizeVersionInfo(null, fallback);

    expect(normalized.version).toBe('1.3.0');
    expect(normalized.updateSupported).toBe(false);
    expect(normalized.buildTime).toBe('');
    expect(normalized.platform).toBe('unknown');
  });

  it('keeps backend values and only defaults missing fields', () => {
    const fallback = fallbackVersionInfo('1.3.0', 'geek-xin/web-router');
    const normalized = normalizeVersionInfo({ version: '1.4.0', platform: 'linux-x64', updateSupported: true }, fallback);

    expect(normalized.version).toBe('1.4.0');
    expect(normalized.platform).toBe('linux-x64');
    expect(normalized.updateSupported).toBe(true);
    expect(normalized.installMode).toBe('jar');
  });

  it('formats build time and byte sizes', () => {
    expect(formatBuildTime('')).toBe('—');
    expect(formatBuildTime('not-a-date')).toBe('not-a-date');
    expect(formatBuildTime('2026-09-30T15:56:37Z')).toMatch(/^2026-09-3\d \d{2}:\d{2}$/);
    expect(formatBytes(0)).toBe('—');
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(52_428_800)).toBe('50 MB');
  });

  it('summarizes update status for the three states', () => {
    expect(updateStatusText(null, true)).toBe('正在检查更新…');
    expect(updateStatusText(null, false)).toBe('尚未检查更新');
    expect(updateStatusText(available, false)).toBe('发现新版本 1.4.0');
    expect(updateStatusText({ ...available, updateAvailable: false, message: '当前已是最新版本' }, false)).toBe('当前已是最新版本');
  });

  it('only allows applying when an asset exists and the install supports updates', () => {
    expect(canApplyUpdate(available, info)).toBe(true);
    expect(canApplyUpdate(available, { ...info, updateSupported: false })).toBe(false);
    expect(canApplyUpdate({ ...available, assetUrl: '' }, info)).toBe(false);
    expect(canApplyUpdate(null, info)).toBe(false);

    expect(applyDisabledReason(available, info)).toBe('');
    expect(applyDisabledReason(null, info)).toBe('没有可用更新');
    expect(applyDisabledReason({ ...available, assetUrl: '' }, info)).toBe('该版本没有匹配当前安装方式的可下载资产');
    expect(applyDisabledReason(available, { ...info, updateSupported: false })).toBe('当前安装方式不支持自动更新，请手动下载安装包');
  });
});
