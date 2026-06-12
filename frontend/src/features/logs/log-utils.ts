import type { LogViewState, ProxyRequestLogEntry, ProxyRequestLogSnapshot } from './types';

export const ROUTE_LOG_MAX_RECENT = 100;
export const ROUTE_LOG_SLOW_THRESHOLD_MS = 1000;

export function normalizedLogPath(path?: string | null): string {
  return (path || '').trim() || '/';
}

export function duration(entry: Pick<ProxyRequestLogEntry, 'durationMs'>): number {
  return Number(entry.durationMs || 0);
}

export function buildPathStats(logs: Array<Pick<ProxyRequestLogEntry, 'path'>>): Record<string, number> {
  return logs.reduce<Record<string, number>>((acc, entry) => {
    const path = normalizedLogPath(entry.path);
    acc[path] = (acc[path] || 0) + 1;
    return acc;
  }, {});
}

export function buildPathDurationStats(logs: Array<Pick<ProxyRequestLogEntry, 'path' | 'durationMs'>>): Record<string, number> {
  return logs.reduce<Record<string, number>>((acc, entry) => {
    const path = normalizedLogPath(entry.path);
    acc[path] = (acc[path] || 0) + duration(entry);
    return acc;
  }, {});
}

export function buildPathMaxDurationStats(logs: Array<Pick<ProxyRequestLogEntry, 'path' | 'durationMs'>>): Record<string, number> {
  return logs.reduce<Record<string, number>>((acc, entry) => {
    const path = normalizedLogPath(entry.path);
    acc[path] = Math.max(acc[path] || 0, duration(entry));
    return acc;
  }, {});
}

export function compareDurationTopLogs(left: ProxyRequestLogEntry, right: ProxyRequestLogEntry): number {
  const byDuration = duration(right) - duration(left);
  if (byDuration !== 0) {
    return byDuration;
  }
  const leftTime = left.time ? new Date(left.time).getTime() : 0;
  const rightTime = right.time ? new Date(right.time).getTime() : 0;
  const byTime = rightTime - leftTime;
  if (byTime !== 0) {
    return byTime;
  }
  return normalizedLogPath(left.path).localeCompare(normalizedLogPath(right.path));
}

export function buildDurationTopLogs(logs: ProxyRequestLogEntry[], limit = ROUTE_LOG_MAX_RECENT): ProxyRequestLogEntry[] {
  return [...logs].sort(compareDurationTopLogs).slice(0, limit);
}

export function updateDurationTopLogs(logs: ProxyRequestLogEntry[], entry: ProxyRequestLogEntry, limit = ROUTE_LOG_MAX_RECENT): ProxyRequestLogEntry[] {
  return buildDurationTopLogs([entry, ...logs], limit);
}

export function snapshotToState(snapshot: ProxyRequestLogSnapshot): LogViewState {
  const recentLogs = snapshot.recentLogs || [];
  return {
    totalRequests: snapshot.totalRequests || recentLogs.length,
    totalDurationMs: snapshot.totalDurationMs || recentLogs.reduce((total, entry) => total + duration(entry), 0),
    requestsByIp: snapshot.requestsByIp || {},
    pathStats: snapshot.pathStats || buildPathStats(recentLogs),
    pathDurationStats: snapshot.pathDurationStats || buildPathDurationStats(recentLogs),
    pathMaxDurationStats: snapshot.pathMaxDurationStats || buildPathMaxDurationStats(recentLogs),
    durationTopLogs: (snapshot.durationTopLogs || buildDurationTopLogs(recentLogs)).slice(0, ROUTE_LOG_MAX_RECENT),
    recentLogs: recentLogs.slice(0, ROUTE_LOG_MAX_RECENT),
  };
}

export function addLogEntry(state: LogViewState, entry: ProxyRequestLogEntry): LogViewState {
  const path = normalizedLogPath(entry.path);
  const entryDuration = duration(entry);
  return {
    totalRequests: state.totalRequests + 1,
    totalDurationMs: state.totalDurationMs + entryDuration,
    requestsByIp: state.requestsByIp,
    pathStats: { ...state.pathStats, [path]: (state.pathStats[path] || 0) + 1 },
    pathDurationStats: { ...state.pathDurationStats, [path]: (state.pathDurationStats[path] || 0) + entryDuration },
    pathMaxDurationStats: { ...state.pathMaxDurationStats, [path]: Math.max(state.pathMaxDurationStats[path] || 0, entryDuration) },
    durationTopLogs: updateDurationTopLogs(state.durationTopLogs, entry),
    recentLogs: [entry, ...state.recentLogs].slice(0, ROUTE_LOG_MAX_RECENT),
  };
}

export function errorCount(logs: ProxyRequestLogEntry[]): number {
  return logs.filter((entry) => Number(entry.status || 0) >= 400).length;
}

export function slowCount(logs: ProxyRequestLogEntry[]): number {
  return logs.filter((entry) => duration(entry) >= ROUTE_LOG_SLOW_THRESHOLD_MS).length;
}

export function successRate(totalRequests: number, failures: number): string {
  if (totalRequests <= 0) {
    return '0%';
  }
  return Math.max(0, Math.round(((totalRequests - failures) / totalRequests) * 100)) + '%';
}
