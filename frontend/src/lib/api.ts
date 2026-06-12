export interface ApiResult<T> {
  success: boolean;
  code?: number;
  message?: string;
  data: T;
}

export async function fetchJson<T>(url: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(url, options);
  let result: ApiResult<T>;
  try {
    result = (await response.json()) as ApiResult<T>;
  } catch (error) {
    throw new Error('响应不是有效 JSON: HTTP ' + response.status);
  }
  if (!response.ok || !result.success) {
    throw new Error(result.message || '请求失败: HTTP ' + response.status);
  }
  return result.data;
}

export function jsonRequest<TBody>(body: TBody, method = 'POST'): RequestInit {
  return {
    method,
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  };
}
