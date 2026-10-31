let sessionGeneration = 0;
export function resetApiSession(){sessionGeneration++;csrf=null;}
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
  const generation=sessionGeneration;
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
  if (!r.ok) {
    if(r.status===401&&generation===sessionGeneration){resetApiSession();window.dispatchEvent(new CustomEvent('ockc-session-expired',{detail:data.message||'登录已失效，请重新登录'}));}
    throw new ApiFailure(r.status, data.message || "请求失败");
  }
  return data;
}
// 关页面、切走标签页时也要把最后一段数据送出去：keepalive 请求在页面卸载后仍会发完。
// 失败不提示、不触发登录失效流程，只用于停留时长这类可丢的上报。
export async function apiKeepalive(path: string, body: unknown): Promise<number> {
  if (!csrf) await refreshCsrf();
  const r = await fetch("/api" + path, {
    method: "POST",
    credentials: "same-origin",
    keepalive: true,
    headers: { "Content-Type": "application/json", [csrf!.headerName]: csrf!.token },
    body: JSON.stringify(body),
  });
  return r.status;
}
export async function login(email: string, password: string) {
  resetApiSession();
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
  const generation=sessionGeneration;
  if (!csrf) await refreshCsrf();
  const body = new FormData(); body.append('file', file);
  const r = await fetch(`/api/entries/${entryId}/media`, {
    method: 'POST', credentials: 'same-origin',
    headers: { [csrf!.headerName]: csrf!.token }, body,
  });
  const data = await r.json().catch(() => ({ message: '图片上传失败' }));
  if (!r.ok) {if(r.status===401&&generation===sessionGeneration){resetApiSession();window.dispatchEvent(new CustomEvent('ockc-session-expired',{detail:data.message||'登录已失效，请重新登录'}));}throw new ApiFailure(r.status, data.message || '图片上传失败');}
  return data;
}

// File downloads use the same session epoch as JSON requests. Never download a stale user's response.
export async function fetchCsv(path:string,signal?:AbortSignal):Promise<Blob>{
 const generation=sessionGeneration;
 const r=await fetch('/api'+path,{credentials:'same-origin',signal,headers:{Accept:'text/csv'}});
 if(!r.ok){const data=await r.json().catch(()=>({message:'报告导出失败'}));if(r.status===401&&generation===sessionGeneration){resetApiSession();window.dispatchEvent(new CustomEvent('ockc-session-expired',{detail:data.message||'登录已失效，请重新登录'}));}throw new ApiFailure(r.status,data.message||'报告导出失败');}
 if(generation!==sessionGeneration)throw new Error('登录身份已切换，请重新生成报告');
 if(!r.headers.get('content-type')?.toLowerCase().startsWith('text/csv'))throw new Error('报告格式不正确，未下载该响应');
 if(Number(r.headers.get('content-length'))>20*1024*1024)throw new Error('报告超过下载容量限制，请缩小范围');
 const file=await r.blob();if(generation!==sessionGeneration)throw new Error('登录身份已切换，请重新生成报告');if(file.size>20*1024*1024)throw new Error('报告超过下载容量限制，请缩小范围');return file;
}
