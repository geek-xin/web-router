export interface ProxyRequestLogEntry {
  time?: string | null;
  routeId?: string | null;
  method?: string | null;
  path?: string | null;
  clientIp?: string | null;
  status?: number | null;
  durationMs?: number | null;
  accessAddress?: string | null;
  requestParams?: string | null;
  requestBody?: string | null;
  responseBody?: string | null;
}

export interface ProxyRequestLogSnapshot {
  totalRequests: number;
  totalDurationMs?: number | null;
  requestsByIp?: Record<string, number> | null;
  pathStats?: Record<string, number> | null;
  pathDurationStats?: Record<string, number> | null;
  pathMaxDurationStats?: Record<string, number> | null;
  durationTopLogs?: ProxyRequestLogEntry[] | null;
  recentLogs?: ProxyRequestLogEntry[] | null;
}

export interface LogViewState {
  totalRequests: number;
  totalDurationMs: number;
  requestsByIp: Record<string, number>;
  pathStats: Record<string, number>;
  pathDurationStats: Record<string, number>;
  pathMaxDurationStats: Record<string, number>;
  durationTopLogs: ProxyRequestLogEntry[];
  recentLogs: ProxyRequestLogEntry[];
}
