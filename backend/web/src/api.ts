import type {
  KPI,
  Preset,
  PurchasePage,
  Revenue,
  UserPage,
} from './types';

const TOKEN_KEY = 'subflow_admin_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY) || '';
export const setToken = (t: string) => localStorage.setItem(TOKEN_KEY, t);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

/** Raised when the admin token is missing or rejected. Unlocks the login screen. */
export class AdminAuthError extends Error {}

/**
 * Raised for any other non-2xx response, carrying the server's own message.
 *
 * This exists because the console previously awaited its mutations without
 * looking at the status: a 500 from `POST /admin/presets` closed the modal and
 * refreshed the table, so a failed write was indistinguishable from a
 * successful one until you noticed the row was missing.
 */
export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

/** Pulls the most useful message out of a response body, whatever its shape. */
async function errorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json();
    if (typeof body?.error === 'string') return body.error;
    if (typeof body?.message === 'string') return body.message;
  } catch {
    // Not JSON — fall through to the status line.
  }
  return `${res.status} ${res.statusText}`;
}

/**
 * Every /admin call carries the operator token. The admin API can change
 * entitlements, so the server rejects it outright without one.
 */
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(path, {
      ...init,
      headers: { ...(init.headers || {}), 'X-Admin-Token': getToken() },
    });
  } catch {
    // fetch only rejects when the request never left the machine. Status 0
    // marks the difference between "the server said no" and "no server".
    throw new ApiError('Could not reach the server. Is it still running?', 0);
  }

  if (res.status === 401 || res.status === 503) {
    throw new AdminAuthError(await errorMessage(res));
  }
  if (!res.ok) {
    throw new ApiError(await errorMessage(res), res.status);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// Reads. Presets and rates are public endpoints; the rest are token gated.

export const fetchKpi = () => request<KPI>('/api/v1/admin/kpi');

export const fetchPresets = () =>
  request<{ presets: Preset[] | null }>('/api/v1/presets').then(
    r => r.presets ?? [],
  );

export const fetchRates = () =>
  request<{
    base_currency: string;
    rates: Record<string, number> | null;
    updated_at: string;
    provider?: string;
    provider_url?: string;
  }>('/api/v1/rates').then(r => ({
    base: r.base_currency || 'USD',
    rates: r.rates ?? {},
    updatedAt: r.updated_at,
    provider: r.provider ?? '',
    providerUrl: r.provider_url ?? '',
  }));

export const fetchUsers = (limit: number, offset: number) =>
  request<UserPage>(`/api/v1/admin/users?limit=${limit}&offset=${offset}`);

export const fetchPurchases = (limit: number, offset: number) =>
  request<PurchasePage>(
    `/api/v1/admin/purchases?limit=${limit}&offset=${offset}`,
  );

export const fetchRevenue = () => request<Revenue>('/api/v1/admin/revenue');

// Writes.

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export const setUserPro = (userId: string, isPro: boolean, tier: string) =>
  request<{ message: string }>(
    `/api/v1/admin/users/${encodeURIComponent(userId)}/pro`,
    json({ is_pro: isPro, tier }),
  );

export const savePreset = (preset: Preset) =>
  request<{ message: string }>('/api/v1/admin/presets', json(preset));

export const deletePreset = (id: string) =>
  request<{ message: string }>(
    `/api/v1/admin/presets/${encodeURIComponent(id)}`,
    { method: 'DELETE' },
  );

export const seedDemoData = () =>
  request<{ seeded: number; message: string }>('/api/v1/admin/seed', {
    method: 'POST',
  });
