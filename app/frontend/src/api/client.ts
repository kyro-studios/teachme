// API のクライアント。すべての API は、/api の下で、同じエラーの形で返す (docs/api/openapi.yaml の共通の決まり)

const API_BASE = "/api";

/** エラーの本体。`message` は、画面に出してよい日本語の文言 */
export type ApiErrorBody = {
  code: string;
  message: string;
  fields?: Record<string, string>;
};

/** API が失敗したときの例外。`status` と `code` で、画面が取る行動を決める */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string> | undefined;

  constructor(status: number, body: ApiErrorBody) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.fields = body.fields;
  }
}

export type ApiRequestInit = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** JSON にする値。FormData のときは、そのまま送る (画像や録音) */
  body?: unknown;
  /** 画面を離れるときの呼び出しに使う。ページが閉じられても、リクエストを送り切る */
  keepalive?: boolean;
  signal?: AbortSignal;
};

async function toApiError(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as { error?: ApiErrorBody } | null;
  if (body?.error) {
    return new ApiError(response.status, body.error);
  }
  return new ApiError(response.status, {
    code: "unknown",
    message: "サーバーとの通信に失敗しました",
  });
}

/**
 * API を呼び、JSON の応答を返す。失敗したときは ApiError を投げる。
 * 204 (本文なし) のときは undefined を返す。
 */
export async function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  const { method = "GET", body, keepalive, signal } = init;

  const headers: Record<string, string> = {};
  let payload: BodyInit | undefined;
  if (body instanceof FormData) {
    payload = body;
  } else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: payload,
    keepalive,
    signal,
  });
  if (!response.ok) {
    throw await toApiError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
