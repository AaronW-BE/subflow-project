import React, { useState, useEffect } from 'react';
import { 
  LayoutDashboard, 
  Boxes, 
  Users, 
  DollarSign, 
  TrendingUp, 
  Plus, 
  Trash2, 
  ShieldCheck, 
  CheckCircle2, 
  
  ExternalLink,
  Search,
  Sparkles,
  RefreshCw,
  Lock,
  Receipt
} from 'lucide-react';

interface KPI {
  total_users: number;
  dau: number;
  mau: number;
  total_tracked_subs: number;
  pro_subscribers: number;
  pro_conversion_rate: number;
  estimated_mrr: number;
  estimated_arr: number;
  top_tracked_services: Array<{
    name: string;
    count: number;
    percentage: number;
    icon_url: string;
  }>;
  user_growth_trend: Array<{
    date: string;
    new_users: number;
    cumulative_subs: number;
    mrr: number;
  }>;
  category_distribution: Record<string, number>;
}

interface Preset {
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

interface User {
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

interface Purchase {
  purchase_token: string;
  user_id: string;
  product_id: string;
  order_id: string;
  pro_tier: string;
  state: string;
  reported_at: string;
}

interface Revenue {
  purchases_by_tier: Record<string, number>;
  estimated_mrr: number;
  estimated_arr: number;
  lifetime_sales: number;
  purchases_last_30_days: number;
}

const TOKEN_KEY = 'subflow_admin_token';

/** Raised when the admin token is missing or rejected. */
class AdminAuthError extends Error {}

/**
 * Every /admin call carries the operator token. The admin API can change
 * entitlements, so the server rejects it outright without one.
 */
async function apiFetch(path: string, init: RequestInit = {}) {
  const token = localStorage.getItem(TOKEN_KEY) || '';
  const res = await fetch(path, {
    ...init,
    headers: {
      ...(init.headers || {}),
      'X-Admin-Token': token,
    },
  });
  if (res.status === 401 || res.status === 503) {
    throw new AdminAuthError('Admin token rejected');
  }
  return res;
}

export function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'presets' | 'users' | 'rates' | 'revenue'>('overview');
  const [authed, setAuthed] = useState(() => !!localStorage.getItem(TOKEN_KEY));
  const [tokenInput, setTokenInput] = useState('');
  const [authError, setAuthError] = useState('');
  const [purchases, setPurchases] = useState<Purchase[]>([]);
  const [revenue, setRevenue] = useState<Revenue | null>(null);
  const [kpi, setKpi] = useState<KPI | null>(null);
  const [presets, setPresets] = useState<Preset[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [rates, setRates] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(false);
  const [categoryFilter, setCategoryFilter] = useState('All');
  const [searchQuery, setSearchQuery] = useState('');

  // New Preset Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newPreset, setNewPreset] = useState({
    id: '',
    name: '',
    category: 'Entertainment',
    brand_color: '#5856D6',
    icon_url: '',
    default_cycle: 'monthly',
    default_amount_usd: 9.99,
    website_url: '',
    is_popular: true,
  });

  const fetchData = async () => {
    setLoading(true);
    try {
      const [kpiRes, presetsRes, usersRes, ratesRes, purchasesRes, revenueRes] = await Promise.all([
        apiFetch('/api/v1/admin/kpi').then(r => r.json()),
        fetch('/api/v1/presets').then(r => r.json()),
        apiFetch('/api/v1/admin/users').then(r => r.json()),
        fetch('/api/v1/rates').then(r => r.json()),
        apiFetch('/api/v1/admin/purchases').then(r => r.json()),
        apiFetch('/api/v1/admin/revenue').then(r => r.json()),
      ]);
      setKpi(kpiRes);
      setPresets(presetsRes.presets || []);
      setUsers(usersRes.users || []);
      setRates(ratesRes.rates || {});
      setPurchases(purchasesRes.purchases || []);
      setRevenue(revenueRes);
      setAuthed(true);
      setAuthError('');
    } catch (err) {
      if (err instanceof AdminAuthError) {
        localStorage.removeItem(TOKEN_KEY);
        setAuthed(false);
        setAuthError('That token was rejected. Check the ADMIN_TOKEN the server printed at startup.');
      } else {
        console.error('Failed to fetch dashboard data:', err);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (authed) fetchData();
  }, [authed]);

  const handleUnlock = (e: React.FormEvent) => {
    e.preventDefault();
    if (!tokenInput.trim()) return;
    localStorage.setItem(TOKEN_KEY, tokenInput.trim());
    setTokenInput('');
    setAuthed(true);
  };

  const handleLock = () => {
    localStorage.removeItem(TOKEN_KEY);
    setAuthed(false);
  };

  const handleSeedDemo = async () => {
    await apiFetch('/api/v1/admin/seed', { method: 'POST' });
    fetchData();
  };

  const handleTogglePro = async (user: User) => {
    await apiFetch(`/api/v1/admin/users/${user.id}/pro`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        is_pro: !user.is_pro,
        tier: !user.is_pro ? 'annual' : 'free',
      }),
    });
    fetchData();
  };

  const handleCreatePreset = async (e: React.FormEvent) => {
    e.preventDefault();
    const payload = {
      ...newPreset,
      id: newPreset.id || newPreset.name.toLowerCase().replace(/\s+/g, '_'),
      default_amount_usd: Number(newPreset.default_amount_usd),
    };
    await apiFetch('/api/v1/admin/presets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    setIsModalOpen(false);
    fetchData();
  };

  const handleDeletePreset = async (id: string) => {
    if (confirm('Delete preset?')) {
      await apiFetch(`/api/v1/admin/presets/${id}`, { method: 'DELETE' });
      fetchData();
    }
  };

  const filteredPresets = presets.filter(p => {
    const matchesCat = categoryFilter === 'All' || p.category === categoryFilter;
    const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesCat && matchesSearch;
  });

  if (!authed) {
    return (
      <div className="min-h-screen bg-[#F2F2F7] text-[#1C1C1E] flex items-center justify-center p-6">
        <form
          onSubmit={handleUnlock}
          className="w-full max-w-sm bg-white rounded-2xl border border-[#E5E5EA] p-7 shadow-xs"
        >
          <div className="w-11 h-11 rounded-xl bg-[#5856D6] flex items-center justify-center text-white mb-5">
            <Lock className="w-5 h-5" />
          </div>
          <h1 className="text-xl font-bold tracking-tight">Admin Console</h1>
          <p className="text-sm text-[#8E8E93] mt-1 leading-relaxed">
            Enter the operator token. It is the value of <code className="font-mono text-[#5856D6]">ADMIN_TOKEN</code>,
            or the one printed in the server log at startup.
          </p>
          <input
            type="password"
            value={tokenInput}
            onChange={e => setTokenInput(e.target.value)}
            autoFocus
            placeholder="Admin token"
            className="w-full mt-5 px-3.5 py-2.5 rounded-xl border border-[#E5E5EA] text-sm font-mono outline-none focus:border-[#5856D6] transition"
          />
          {authError && (
            <p className="text-xs text-[#FF3B30] mt-2.5 leading-relaxed">{authError}</p>
          )}
          <button
            type="submit"
            disabled={!tokenInput.trim()}
            className="w-full mt-5 px-4 py-2.5 rounded-xl bg-[#5856D6] text-white text-sm font-semibold hover:bg-[#4745B8] disabled:opacity-40 transition"
          >
            Unlock
          </button>
        </form>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-[#F2F2F7] text-[#1C1C1E]">
      {/* Sidebar (Apple macOS Executive Style) */}
      <aside className="w-64 border-r border-[#E5E5EA] bg-white/80 backdrop-blur-xl flex flex-col justify-between p-4 fixed h-full z-20">
        <div>
          {/* Logo & Brand */}
          <div className="flex items-center gap-3 px-3 py-4 mb-4">
            <div className="w-10 h-10 rounded-xl bg-[#5856D6] flex items-center justify-center text-white shadow-sm font-bold text-xl">
              S
            </div>
            <div>
              <h1 className="font-bold text-base tracking-tight leading-tight">SubFlow</h1>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className="w-2 h-2 rounded-full bg-[#34C759]"></span>
                <span className="text-xs text-[#8E8E93] font-medium">Console v1.0</span>
              </div>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1">
            {[
              { id: 'overview', label: 'Overview & KPIs', icon: LayoutDashboard },
              { id: 'presets', label: 'Catalog Presets', icon: Boxes, badge: presets.length },
              { id: 'users', label: 'Users & Pro Tiers', icon: Users, badge: users.length },
              { id: 'revenue', label: 'Revenue & Purchases', icon: Receipt, badge: purchases.length },
              { id: 'rates', label: 'FX Exchange Rates', icon: DollarSign },
            ].map(item => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveTab(item.id as any)}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl font-medium text-sm transition-all ${
                    isActive 
                      ? 'bg-[#5856D6] text-white shadow-sm' 
                      : 'text-[#5D5E63] hover:bg-[#F2F2F7] hover:text-[#1C1C1E]'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <Icon className="w-4 h-4" />
                    <span>{item.label}</span>
                  </div>
                  {item.badge !== undefined && (
                    <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                      isActive ? 'bg-white/20 text-white' : 'bg-[#E5E5EA] text-[#5D5E63]'
                    }`}>
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        {/* Quick Actions & Status */}
        <div className="border-t border-[#E5E5EA] pt-4 space-y-2">
          <button
            onClick={handleSeedDemo}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl bg-[#F2F2F7] text-xs font-semibold text-[#5856D6] hover:bg-[#E5E5EA] transition"
          >
            <Sparkles className="w-3.5 h-3.5" />
            Seed Demo Sample Data
          </button>
          <button
            onClick={handleLock}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl bg-[#F2F2F7] text-xs font-semibold text-[#8E8E93] hover:bg-[#E5E5EA] transition"
          >
            <Lock className="w-3.5 h-3.5" />
            Lock Console
          </button>
          <div className="px-3 py-2 text-xs text-[#8E8E93] text-center">
            Local-First Sync Backend
          </div>
        </div>
      </aside>

      {/* Main Content Stage */}
      <main className="ml-64 flex-1 p-8 min-w-0">
        {/* Header Bar */}
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">
              {activeTab === 'overview' && 'Executive Overview'}
              {activeTab === 'presets' && 'Subscription Catalog Presets'}
              {activeTab === 'users' && 'Users & Subscription Entitlements'}
              {activeTab === 'revenue' && 'Revenue & Play Purchases'}
              {activeTab === 'rates' && 'Global Foreign Exchange Rates'}
            </h2>
            <p className="text-sm text-[#8E8E93] mt-0.5">
              Live tracking metrics for Google Play deployments
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={fetchData}
              disabled={loading}
              className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-[#E5E5EA] text-sm font-medium hover:bg-[#F2F2F7] transition shadow-xs"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              Refresh
            </button>
            {activeTab === 'presets' && (
              <button
                onClick={() => setIsModalOpen(true)}
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#5856D6] text-white text-sm font-medium hover:bg-[#4745B8] transition shadow-sm"
              >
                <Plus className="w-4 h-4" />
                Add Preset
              </button>
            )}
          </div>
        </div>

        {/* Tab 1: Overview & KPIs */}
        {activeTab === 'overview' && kpi && (
          <div className="space-y-6">
            {/* 4 KPI Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="bg-white p-5 rounded-2xl border border-[#E5E5EA] shadow-xs">
                <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-[#8E8E93] mb-2">
                  <span>Total Users</span>
                  <Users className="w-4 h-4 text-[#5856D6]" />
                </div>
                <div className="text-3xl font-bold tracking-tight font-mono">{kpi.total_users}</div>
                <div className="flex items-center gap-2 mt-2 text-xs text-[#34C759] font-medium">
                  <TrendingUp className="w-3 h-3" />
                  <span>DAU: {kpi.dau} • MAU: {kpi.mau}</span>
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-[#E5E5EA] shadow-xs">
                <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-[#8E8E93] mb-2">
                  <span>Tracked Subscriptions</span>
                  <Boxes className="w-4 h-4 text-[#FF9500]" />
                </div>
                <div className="text-3xl font-bold tracking-tight font-mono">{kpi.total_tracked_subs}</div>
                <div className="mt-2 text-xs text-[#8E8E93]">
                  Avg {(kpi.total_users > 0 ? (kpi.total_tracked_subs / kpi.total_users).toFixed(1) : 0)} subs / user
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-[#E5E5EA] shadow-xs">
                <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-[#8E8E93] mb-2">
                  <span>Pro Conversion</span>
                  <ShieldCheck className="w-4 h-4 text-[#34C759]" />
                </div>
                <div className="text-3xl font-bold tracking-tight font-mono">{kpi.pro_conversion_rate.toFixed(1)}%</div>
                <div className="mt-2 text-xs text-[#5856D6] font-medium">
                  {kpi.pro_subscribers} paying Pro subscribers
                </div>
              </div>

              <div className="bg-white p-5 rounded-2xl border border-[#E5E5EA] shadow-xs">
                <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-[#8E8E93] mb-2">
                  <span>Estimated MRR / ARR</span>
                  <DollarSign className="w-4 h-4 text-[#34C759]" />
                </div>
                <div className="text-3xl font-bold tracking-tight font-mono">${kpi.estimated_mrr.toFixed(0)}</div>
                <div className="mt-2 text-xs text-[#8E8E93]">
                  Annualized Run Rate: ${kpi.estimated_arr.toFixed(0)}
                </div>
              </div>
            </div>

            {/* Middle Section: Top Services & Category Distribution */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Top Tracked Services */}
              <div className="bg-white p-6 rounded-2xl border border-[#E5E5EA] shadow-xs">
                <h3 className="font-bold text-base mb-4">Top Tracked Subscriptions</h3>
                <div className="space-y-4">
                  {kpi.top_tracked_services.length === 0 ? (
                    <p className="text-sm text-[#8E8E93]">No subscriptions tracked yet. Click "Seed Demo Data" above.</p>
                  ) : (
                    kpi.top_tracked_services.map((item, idx) => (
                      <div key={idx} className="space-y-1.5">
                        <div className="flex justify-between items-center text-sm font-medium">
                          <span className="flex items-center gap-2">
                            <span className="w-5 text-xs text-[#8E8E93] font-mono font-bold">#{idx + 1}</span>
                            {item.name}
                          </span>
                          <span className="font-mono text-xs font-bold text-[#5856D6]">{item.count} users ({item.percentage.toFixed(0)}%)</span>
                        </div>
                        <div className="w-full h-2 bg-[#F2F2F7] rounded-full overflow-hidden">
                          <div
                            className="h-full bg-[#5856D6] rounded-full transition-all duration-500"
                            style={{ width: `${Math.min(item.percentage * 2, 100)}%` }}
                          />
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* Category Breakdown */}
              <div className="bg-white p-6 rounded-2xl border border-[#E5E5EA] shadow-xs">
                <h3 className="font-bold text-base mb-4">Expense Category Breakdown</h3>
                <div className="grid grid-cols-2 gap-3">
                  {Object.entries(kpi.category_distribution).map(([cat, count]) => (
                    <div key={cat} className="p-3.5 rounded-xl bg-[#F8F8FA] border border-[#E5E5EA]">
                      <div className="text-xs text-[#8E8E93] font-medium">{cat}</div>
                      <div className="text-2xl font-bold font-mono mt-1 text-[#1C1C1E]">{count}</div>
                    </div>
                  ))}
                  {Object.keys(kpi.category_distribution).length === 0 && (
                    <p className="text-sm text-[#8E8E93] col-span-2">No category data available yet.</p>
                  )}
                </div>
              </div>
            </div>

            {/* Growth Trend */}
            <div className="bg-white p-6 rounded-2xl border border-[#E5E5EA] shadow-xs">
              <h3 className="font-bold text-base mb-4">7-Day Trajectory Overview</h3>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs font-semibold">
                      <th className="pb-3">Date</th>
                      <th className="pb-3">Est. New Users</th>
                      <th className="pb-3">Cumulative Tracked</th>
                      <th className="pb-3">Projected MRR</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#E5E5EA] font-mono text-xs">
                    {kpi.user_growth_trend.map((pt, i) => (
                      <tr key={i} className="hover:bg-[#F2F2F7] transition">
                        <td className="py-3 font-sans font-medium text-sm text-[#1C1C1E]">{pt.date}</td>
                        <td className="py-3 text-[#34C759]">+{pt.new_users}</td>
                        <td className="py-3">{pt.cumulative_subs}</td>
                        <td className="py-3 font-bold text-[#5856D6]">${pt.mrr.toFixed(2)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: Catalog Presets */}
        {activeTab === 'presets' && (
          <div className="bg-white rounded-2xl border border-[#E5E5EA] shadow-xs p-6 space-y-6">
            {/* Filters & Search */}
            <div className="flex flex-col sm:flex-row justify-between gap-4">
              <div className="flex gap-2 overflow-x-auto pb-1">
                {['All', 'Entertainment', 'Productivity', 'Cloud', 'Utilities', 'Health'].map(cat => (
                  <button
                    key={cat}
                    onClick={() => setCategoryFilter(cat)}
                    className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition ${
                      categoryFilter === cat
                        ? 'bg-[#5856D6] text-white'
                        : 'bg-[#F2F2F7] text-[#5D5E63] hover:bg-[#E5E5EA]'
                    }`}
                  >
                    {cat}
                  </button>
                ))}
              </div>
              <div className="relative">
                <Search className="w-4 h-4 absolute left-3 top-2.5 text-[#8E8E93]" />
                <input
                  type="text"
                  placeholder="Search 50+ presets..."
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  className="pl-9 pr-4 py-1.5 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-xs focus:outline-none focus:ring-2 focus:ring-[#5856D6]"
                />
              </div>
            </div>

            {/* Presets Table */}
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs font-semibold">
                    <th className="pb-3">Service</th>
                    <th className="pb-3">Category</th>
                    <th className="pb-3">Default Cycle</th>
                    <th className="pb-3">Default Price</th>
                    <th className="pb-3">Status</th>
                    <th className="pb-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E5E5EA]">
                  {filteredPresets.map(preset => (
                    <tr key={preset.id} className="hover:bg-[#F8F8FA] transition">
                      <td className="py-3 flex items-center gap-3">
                        <div
                          className="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-white text-xs shadow-xs"
                          style={{ backgroundColor: preset.brand_color || '#5856D6' }}
                        >
                          {preset.name.charAt(0)}
                        </div>
                        <div>
                          <div className="font-semibold text-sm">{preset.name}</div>
                          {preset.website_url && (
                            <a
                              href={preset.website_url}
                              target="_blank"
                              rel="noreferrer"
                              className="text-xs text-[#8E8E93] hover:text-[#5856D6] flex items-center gap-1"
                            >
                              website <ExternalLink className="w-2.5 h-2.5" />
                            </a>
                          )}
                        </div>
                      </td>
                      <td className="py-3">
                        <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-[#F2F2F7] text-[#5D5E63]">
                          {preset.category}
                        </span>
                      </td>
                      <td className="py-3 text-xs capitalize font-medium">{preset.default_cycle}</td>
                      <td className="py-3 font-mono font-semibold">${preset.default_amount_usd.toFixed(2)}</td>
                      <td className="py-3">
                        {preset.is_popular ? (
                          <span className="inline-flex items-center gap-1 text-xs font-semibold text-[#FF9500]">
                            <Sparkles className="w-3 h-3" /> Popular
                          </span>
                        ) : (
                          <span className="text-xs text-[#8E8E93]">Standard</span>
                        )}
                      </td>
                      <td className="py-3 text-right">
                        <button
                          onClick={() => handleDeletePreset(preset.id)}
                          className="p-1.5 text-[#FF3B30] hover:bg-red-50 rounded-lg transition"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 3: Users & Pro Tiers */}
        {activeTab === 'users' && (
          <div className="bg-white rounded-2xl border border-[#E5E5EA] shadow-xs p-6">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs font-semibold">
                    <th className="pb-3">User</th>
                    <th className="pb-3">Provider</th>
                    <th className="pb-3">Pro Status</th>
                    <th className="pb-3">Tier</th>
                    <th className="pb-3">Joined Date</th>
                    <th className="pb-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#E5E5EA]">
                  {users.map(u => (
                    <tr key={u.id} className="hover:bg-[#F8F8FA] transition">
                      <td className="py-3 flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-[#E5E5EA] text-[#1C1C1E] flex items-center justify-center font-bold text-xs">
                          {u.name ? u.name.charAt(0) : 'U'}
                        </div>
                        <div>
                          <div className="font-semibold text-sm">{u.name || 'Anonymous User'}</div>
                          <div className="text-xs text-[#8E8E93]">{u.email}</div>
                        </div>
                      </td>
                      <td className="py-3">
                        <span className="text-xs px-2 py-0.5 rounded-md bg-[#F2F2F7] font-mono uppercase">
                          {u.auth_provider}
                        </span>
                      </td>
                      <td className="py-3">
                        {u.is_pro ? (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-green-100 text-[#34C759]">
                            <CheckCircle2 className="w-3 h-3" /> PRO Active
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#F2F2F7] text-[#8E8E93]">
                            Free Tier
                          </span>
                        )}
                      </td>
                      <td className="py-3 text-xs capitalize font-medium">{u.pro_tier || 'free'}</td>
                      <td className="py-3 text-xs text-[#8E8E93]">
                        {new Date(u.created_at).toLocaleDateString()}
                      </td>
                      <td className="py-3 text-right">
                        <button
                          onClick={() => handleTogglePro(u)}
                          className={`px-3 py-1 rounded-lg text-xs font-semibold transition ${
                            u.is_pro
                              ? 'bg-red-50 text-[#FF3B30] hover:bg-red-100'
                              : 'bg-indigo-50 text-[#5856D6] hover:bg-indigo-100'
                          }`}
                        >
                          {u.is_pro ? 'Revoke Pro' : 'Grant Pro'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 4: Revenue & Purchases */}
        {activeTab === 'revenue' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {[
                { label: 'Estimated MRR', value: `$${(revenue?.estimated_mrr ?? 0).toFixed(2)}`, hint: 'From recorded subscription purchases' },
                { label: 'Estimated ARR', value: `$${(revenue?.estimated_arr ?? 0).toFixed(2)}`, hint: 'MRR x 12' },
                { label: 'Lifetime Sales', value: `${revenue?.lifetime_sales ?? 0}`, hint: 'One-time purchases' },
                { label: 'Last 30 Days', value: `${revenue?.purchases_last_30_days ?? 0}`, hint: 'New purchases reported' },
              ].map(card => (
                <div key={card.label} className="bg-white p-5 rounded-2xl border border-[#E5E5EA] shadow-xs">
                  <div className="text-xs font-semibold uppercase tracking-wider text-[#8E8E93] mb-2">
                    {card.label}
                  </div>
                  <div className="text-3xl font-bold tracking-tight font-mono">{card.value}</div>
                  <div className="mt-2 text-xs text-[#8E8E93]">{card.hint}</div>
                </div>
              ))}
            </div>

            <div className="bg-white rounded-2xl border border-[#E5E5EA] shadow-xs overflow-hidden">
              <div className="px-6 py-4 border-b border-[#E5E5EA]">
                <h3 className="font-bold text-base">Purchase Ledger</h3>
                <p className="text-xs text-[#8E8E93] mt-0.5">
                  Purchase tokens reported by clients. Amounts are estimates from US list prices;
                  settled revenue comes from the Play Console payout reports.
                </p>
              </div>
              {purchases.length === 0 ? (
                <div className="px-6 py-10 text-center text-sm text-[#8E8E93]">
                  No purchases recorded yet.
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="bg-[#F8F8FA] text-xs uppercase tracking-wider text-[#8E8E93]">
                    <tr>
                      <th className="text-left font-semibold px-6 py-3">Product</th>
                      <th className="text-left font-semibold px-6 py-3">Tier</th>
                      <th className="text-left font-semibold px-6 py-3">User</th>
                      <th className="text-left font-semibold px-6 py-3">Order</th>
                      <th className="text-left font-semibold px-6 py-3">Reported</th>
                    </tr>
                  </thead>
                  <tbody>
                    {purchases.map(p => (
                      <tr key={p.purchase_token} className="border-t border-[#F2F2F7]">
                        <td className="px-6 py-3 font-medium">{p.product_id}</td>
                        <td className="px-6 py-3">
                          <span className="text-xs px-2 py-0.5 rounded-full font-semibold bg-[#5856D6]/10 text-[#5856D6]">
                            {p.pro_tier}
                          </span>
                        </td>
                        <td className="px-6 py-3 font-mono text-xs text-[#5D5E63]">{p.user_id}</td>
                        <td className="px-6 py-3 font-mono text-xs text-[#5D5E63]">{p.order_id || '-'}</td>
                        <td className="px-6 py-3 text-xs text-[#8E8E93]">
                          {new Date(p.reported_at).toLocaleString()}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        )}

        {/* Tab 5: FX Rates */}
        {activeTab === 'rates' && (
          <div className="bg-white rounded-2xl border border-[#E5E5EA] shadow-xs p-6">
            <h3 className="font-bold text-base mb-2">Base Currency: USD ($)</h3>
            <p className="text-sm text-[#8E8E93] mb-6">Rates cached in Go memory and utilized for client real-time conversions.</p>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
              {Object.entries(rates).map(([curr, val]) => (
                <div key={curr} className="p-4 rounded-xl border border-[#E5E5EA] bg-[#F8F8FA]">
                  <div className="text-xs font-bold text-[#8E8E93]">{curr}</div>
                  <div className="text-2xl font-bold font-mono mt-1 text-[#1C1C1E]">
                    {val.toFixed(2)}
                  </div>
                  <div className="text-xs text-[#5D5E63] mt-1">1 USD = {val} {curr}</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </main>

      {/* Add Preset Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white w-full max-w-md rounded-2xl p-6 shadow-xl border border-[#E5E5EA]">
            <h3 className="text-lg font-bold mb-4">Create New Subscription Preset</h3>
            <form onSubmit={handleCreatePreset} className="space-y-4 text-sm">
              <div>
                <label className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1">Service Name</label>
                <input
                  required
                  type="text"
                  placeholder="e.g. Paramount+"
                  value={newPreset.name}
                  onChange={e => setNewPreset({ ...newPreset, name: e.target.value })}
                  className="w-full px-3 py-2 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#5856D6]"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1">Category</label>
                  <select
                    value={newPreset.category}
                    onChange={e => setNewPreset({ ...newPreset, category: e.target.value })}
                    className="w-full px-3 py-2 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-sm focus:outline-none"
                  >
                    <option>Entertainment</option>
                    <option>Productivity</option>
                    <option>Cloud</option>
                    <option>Utilities</option>
                    <option>Health</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1">Brand Color</label>
                  <input
                    type="color"
                    value={newPreset.brand_color}
                    onChange={e => setNewPreset({ ...newPreset, brand_color: e.target.value })}
                    className="w-full h-9 p-1 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl cursor-pointer"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1">Default Cycle</label>
                  <select
                    value={newPreset.default_cycle}
                    onChange={e => setNewPreset({ ...newPreset, default_cycle: e.target.value })}
                    className="w-full px-3 py-2 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-sm focus:outline-none"
                  >
                    <option value="weekly">Weekly</option>
                    <option value="monthly">Monthly</option>
                    <option value="annually">Annually</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1">Price (USD $)</label>
                  <input
                    required
                    type="number"
                    step="0.01"
                    value={newPreset.default_amount_usd}
                    onChange={e => setNewPreset({ ...newPreset, default_amount_usd: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-2 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-sm focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1">Website URL</label>
                <input
                  type="url"
                  placeholder="https://..."
                  value={newPreset.website_url}
                  onChange={e => setNewPreset({ ...newPreset, website_url: e.target.value })}
                  className="w-full px-3 py-2 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-sm focus:outline-none"
                />
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="pop"
                  checked={newPreset.is_popular}
                  onChange={e => setNewPreset({ ...newPreset, is_popular: e.target.checked })}
                  className="rounded text-[#5856D6]"
                />
                <label htmlFor="pop" className="text-xs font-medium cursor-pointer">Feature as Popular Preset</label>
              </div>

              <div className="flex justify-end gap-2 pt-4">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-[#5D5E63] hover:bg-[#F2F2F7]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#5856D6] text-white hover:bg-[#4745B8]"
                >
                  Save Preset
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
