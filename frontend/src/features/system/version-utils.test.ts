import { describe, expect, it } from 'vitest';
import {
  applyDisabledReason,
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
import type { AppVersionInfo, UpdateCheckResult } from './types';

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

describe('version-utils', () => {
  it('labels platforms and install modes in Chinese', () => {
    expect(platformLabel('macos-arm64')).toBe('macOS · Apple Silicon');
    expect(platformLabel('windows-x64')).toBe('Windows · x64');
    expect(platformLabel('solaris')).toBe('未识别平台');
    expect(installModeLabel('jar')).toBe('JAR 服务');
    expect(installModeLabel('app-image')).toBe('免安装程序');
    expect(updateChannelLabel('prerelease')).toBe('预览通道');
    expect(updateChannelLabel('stable')).toBe('稳定通道');
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
