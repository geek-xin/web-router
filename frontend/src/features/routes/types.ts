export interface RouteConfig {
  id: string;
  name: string;
  pathPrefix?: string | null;
  pathPrefixes?: string[] | null;
  targetUrl: string;
  accessPageBaseUrl?: string | null;
  accessPage?: string | null;
  localIp?: string | null;
  localPort?: number | null;
  enabled: boolean;
}

export interface RouteConfigPayload {
  name: string;
  pathPrefix?: string | null;
  pathPrefixes: string[];
  targetUrl: string;
  accessPageBaseUrl?: string | null;
  accessPage?: string | null;
  localIp?: string | null;
  localPort?: number | null;
  enabled: boolean;
}

export interface RouteFormValues {
  name: string;
  targetUrl: string;
  accessPageBaseUrl: string;
  accessPage: string;
  localIp: string;
  localPort: string;
  pathPrefixes: string[];
  enabled?: boolean;
}

export interface RouteValidationResult {
  errors: string[];
  payload?: RouteConfigPayload;
}

export type RouteStatus = 'running' | 'stopped' | 'warning' | 'error';

export type RouteSortKey = 'recent' | 'name' | 'traffic' | 'latency';

export type RouteStatusFilter = 'all' | 'enabled' | 'disabled';
