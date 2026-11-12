export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

type Csrf = {headerName: string; token: string};
let csrf: Csrf | undefined;

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  if (!path.startsWith('/api/')) throw new Error('Only same-origin API paths are supported');
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (options.body) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrf) {
      const response = await fetch('/api/csrf', {credentials: 'same-origin'});
      if (!response.ok) throw new ApiError(response.status, 'Unable to establish a secure session. Please reload.');
      csrf = await response.json() as Csrf;
    }
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(path, {...options, method, headers, credentials:'same-origin'});
  if (response.status === 401 || response.status === 403) csrf = undefined;
  if (!response.ok) {
    let detail = 'The request could not be completed. Please try again.';
    try {
      const problem = await response.json() as {detail?: string};
      if (problem.detail) detail = problem.detail;
    } catch { /* A proxy may return a non-JSON response. */ }
    throw new ApiError(response.status, detail);
  }
  if (path === '/api/logout' || path === '/api/auth/demo') csrf = undefined;
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
