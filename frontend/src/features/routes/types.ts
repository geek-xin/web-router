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
