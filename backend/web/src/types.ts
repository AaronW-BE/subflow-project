/** Wire types for the /api/v1 admin surface. Field names match the Go json tags. */

export interface ServicePopularity {
  name: string;
  count: number;
  percentage: number;
  icon_url: string;
}

export interface TrendPoint {
  date: string;
  new_users: number;
  new_purchases: number;
}

export interface KPI {
  total_users: number;
  dau: number;
  mau: number;
  total_tracked_subs: number;
  pro_subscribers: number;
  pro_conversion_rate: number;
  estimated_mrr: number;
  estimated_arr: number;
  top_tracked_services: ServicePopularity[];
  user_growth_trend: TrendPoint[];
  category_distribution: Record<string, number>;
}

export interface Preset {
  id: string;
  name: string;
  category: string;
  brand_color: string;
  icon_url: string;
  default_cycle: string;
  default_amount_usd: number;
  website_url: string;
  is_popular: boolean;
}

export interface User {
  id: string;
  email: string;
  name: string;
  picture: string;
  auth_provider: string;
  is_pro: boolean;
  pro_tier: string;
  created_at: string;
  last_active_at: string;
}

export interface Purchase {
  purchase_token: string;
  user_id: string;
  product_id: string;
  order_id: string;
  pro_tier: string;
  state: string;
  reported_at: string;
}

export interface Revenue {
  purchases_by_tier: Record<string, number>;
  estimated_mrr: number;
  estimated_arr: number;
  lifetime_sales: number;
  lifetime_gross: number;
  purchases_last_30_days: number;
}

/**
 * A page of rows plus the size of the whole table.
 *
 * `total` is what lets the console say "50 of 900" — without it a full page
 * and a complete table look identical, and the sidebar counts read as totals
 * when they are really page sizes.
 */
export interface UserPage {
  users: User[];
  total: number;
  limit: number;
  offset: number;
}

export interface PurchasePage {
  purchases: Purchase[];
  total: number;
  limit: number;
  offset: number;
}

/** Tiers the admin API will accept in a Pro grant. Mirrors model.IsGrantableProTier. */
export const GRANTABLE_TIERS = ['monthly', 'annual', 'lifetime'] as const;
export type GrantableTier = (typeof GRANTABLE_TIERS)[number];

export const PRESET_CATEGORIES = [
  'Entertainment',
  'Productivity',
  'Cloud',
  'Utilities',
  'Health',
] as const;

export type TabId = 'overview' | 'presets' | 'users' | 'revenue' | 'rates';
