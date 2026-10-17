let csrf: { token: string; headerName: string } | null = null;
export class ApiFailure extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function refreshCsrf() {
  const r = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  if (!r.ok) throw new Error("无法获取安全令牌");
  csrf = await r.json();
}
export async function api(
  path: string,
  method = "GET",
  body?: unknown,
  key?: string,
): Promise<any> {
  if (method !== "GET" && !csrf) await refreshCsrf();
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (csrf && method !== "GET") headers[csrf.headerName] = csrf.token;
  if (key) headers["Idempotency-Key"] = key;
  const r = await fetch("/api" + path, {
    method,
    credentials: "same-origin",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await r.json().catch(() => ({ message: "服务返回异常" }));
  if (!r.ok) throw new ApiFailure(r.status, data.message || "请求失败");
  return data;
}
export async function login(email: string, password: string) {
  await refreshCsrf();
  const r = await fetch("/api/auth/login", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      [csrf!.headerName]: csrf!.token,
    },
    body: new URLSearchParams({ username: email, password }),
  });
  if (!r.ok) throw new Error((await r.json()).message || "登录失败");
  await refreshCsrf();
  return api("/auth/me");
}

export async function uploadImage(entryId: number, file: File) {
  if (!csrf) await refreshCsrf();
  const body = new FormData(); body.append('file', file);
  const r = await fetch(`/api/entries/${entryId}/media`, {
    method: 'POST', credentials: 'same-origin',
    headers: { [csrf!.headerName]: csrf!.token }, body,
  });
  const data = await r.json().catch(() => ({ message: '图片上传失败' }));
  if (!r.ok) throw new ApiFailure(r.status, data.message || '图片上传失败');
  return data;
}
