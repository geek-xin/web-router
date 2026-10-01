/** 与后端 AppVersionInfo 对应。 */
export interface AppVersionInfo {
  version: string;
  buildTime: string;
  platform: string;
  installMode: string;
  updateSupported: boolean;
  updateChannel: string;
  repository: string;
}

/** 与后端 UpdateCheckResult 对应。 */
export interface UpdateCheckResult {
  currentVersion: string;
  latestVersion: string;
  updateAvailable: boolean;
  prerelease: boolean;
  releaseName: string;
  releaseNotes: string;
  publishedAt: string;
  releaseUrl: string;
  assetName: string;
  assetUrl: string;
  assetSize: number;
  assetDigest: string;
  message: string;
}

/** 与后端 UpdateApplyResult 对应。 */
export interface UpdateApplyResult {
  version: string;
  archivePath: string;
  updaterScript: string;
  message: string;
}
