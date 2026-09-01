import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Boxes,
  DollarSign,
  LayoutDashboard,
  Lock,
  Plus,
  Receipt,
  RefreshCw,
  Sparkles,
  Users,
} from 'lucide-react';
import * as api from './api';
import { AdminAuthError } from './api';
import { Banner } from './components/ui';
import { LoginView } from './views/LoginView';
import { OverviewView } from './views/OverviewView';
import { PresetFormModal } from './views/PresetFormModal';
import { PresetsView } from './views/PresetsView';
import { RatesView } from './views/RatesView';
import { RevenueView } from './views/RevenueView';
import { UsersView } from './views/UsersView';
import type {
  GrantableTier,
  KPI,
  Preset,
  Purchase,
  Revenue,
  TabId,
  User,
} from './types';

const PAGE_SIZE = 50;
const TAB_KEY = 'subflow_admin_tab';

const TABS: {
  id: TabId;
  label: string;
  heading: string;
  icon: typeof LayoutDashboard;
}[] = [
  { id: 'overview', label: 'Overview', heading: 'Overview', icon: LayoutDashboard },
  { id: 'presets', label: 'Catalog presets', heading: 'Subscription catalog presets', icon: Boxes },
  { id: 'users', label: 'Users & Pro tiers', heading: 'Users and entitlements', icon: Users },
  { id: 'revenue', label: 'Revenue', heading: 'Revenue and Play purchases', icon: Receipt },
  { id: 'rates', label: 'FX rates', heading: 'Foreign exchange rates', icon: DollarSign },
];

function readStoredTab(): TabId {
  const stored = localStorage.getItem(TAB_KEY);
  return TABS.some(t => t.id === stored) ? (stored as TabId) : 'overview';
}

export function App() {
  const [authed, setAuthed] = useState(() => !!api.getToken());
  const [authError, setAuthError] = useState('');
  const [activeTab, setActiveTab] = useState<TabId>(readStoredTab);

  const [kpi, setKpi] = useState<KPI | null>(null);
  const [presets, setPresets] = useState<Preset[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [usersTotal, setUsersTotal] = useState(0);
  const [purchases, setPurchases] = useState<Purchase[]>([]);
  const [purchasesTotal, setPurchasesTotal] = useState(0);
  const [revenue, setRevenue] = useState<Revenue | null>(null);
  const [rates, setRates] = useState<{ base: string; rates: Record<string, number>; updatedAt: string | null }>({
    base: 'USD',
    rates: {},
    updatedAt: null,
  });

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [pendingUserId, setPendingUserId] = useState<string | null>(null);
  const [error, setErrorState] = useState('');
  const [notice, setNoticeState] = useState('');

  // The two banners are mutually exclusive. Leaving a stale "saved" message
  // sitting under a fresh failure reads as though both just happened.
  const setError = useCallback((message: string) => {
    setErrorState(message);
    if (message) setNoticeState('');
  }, []);
  const setNotice = useCallback((message: string) => {
    setNoticeState(message);
    if (message) setErrorState('');
  }, []);
  const [lastLoaded, setLastLoaded] = useState<Date | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  // Guards against a stale response from a superseded refresh overwriting a
  // newer one — Refresh is easy to double-click and the six calls race.
  const requestSeq = useRef(0);

  const lock = useCallback(() => {
    api.clearToken();
    setAuthed(false);
    setKpi(null);
    setPresets([]);
    setUsers([]);
    setPurchases([]);
    setRevenue(null);
  }, []);

  /**
   * Runs an action, turning any failure into something visible.
   *
   * Every mutation used to be a bare `await apiFetch(...)` followed
   * unconditionally by a refresh: a rejected write looked exactly like a
   * successful one, and a 401 became an unhandled promise rejection.
   */
  const run = useCallback(
    async <T,>(action: () => Promise<T>): Promise<T | undefined> => {
      try {
        return await action();
      } catch (err) {
        if (err instanceof AdminAuthError) {
          api.clearToken();
          setAuthed(false);
          setAuthError(
            'That token was rejected. Check the ADMIN_TOKEN the server printed at startup.',
          );
          return undefined;
        }
        setError(err instanceof Error ? err.message : String(err));
        return undefined;
      }
    },
    [setError],
  );

  const loadAll = useCallback(async () => {
    const seq = ++requestSeq.current;
    setLoading(true);
    setError('');
    const ok = await run(async () => {
      const [kpiRes, presetsRes, ratesRes, usersRes, purchasesRes, revenueRes] =
        await Promise.all([
          api.fetchKpi(),
          api.fetchPresets(),
          api.fetchRates(),
          api.fetchUsers(PAGE_SIZE, 0),
          api.fetchPurchases(PAGE_SIZE, 0),
          api.fetchRevenue(),
        ]);
      if (seq !== requestSeq.current) return false; // superseded
      setKpi(kpiRes);
      setPresets(presetsRes);
      setRates(ratesRes);
      setUsers(usersRes.users);
      setUsersTotal(usersRes.total);
      setPurchases(purchasesRes.purchases);
      setPurchasesTotal(purchasesRes.total);
      setRevenue(revenueRes);
      setLastLoaded(new Date());
      return true;
    });
    if (seq === requestSeq.current) setLoading(false);
    return ok === true;
  }, [run, setError]);

  useEffect(() => {
    if (authed) void loadAll();
  }, [authed, loadAll]);

  useEffect(() => {
    localStorage.setItem(TAB_KEY, activeTab);
  }, [activeTab]);

  // Targeted reloads. A preset edit has no bearing on the purchase ledger, and
  // refetching all six endpoints after every write made each click cost six
  // round trips.
  const reloadPresets = useCallback(async () => {
    setLoading(true);
    await run(async () => setPresets(await api.fetchPresets()));
    setLoading(false);
  }, [run]);

  const reloadUsers = useCallback(async () => {
    setLoading(true);
    await run(async () => {
      const [page, kpiRes] = await Promise.all([
        api.fetchUsers(PAGE_SIZE, 0),
        api.fetchKpi(), // entitlement changes move the Pro conversion figure
      ]);
      setUsers(page.users);
      setUsersTotal(page.total);
      setKpi(kpiRes);
    });
    setLoading(false);
  }, [run]);

  const loadMoreUsers = useCallback(async () => {
    setLoading(true);
    await run(async () => {
      const page = await api.fetchUsers(PAGE_SIZE, users.length);
      setUsers(prev => [...prev, ...page.users]);
      setUsersTotal(page.total);
    });
    setLoading(false);
  }, [run, users.length]);

  const loadMorePurchases = useCallback(async () => {
    setLoading(true);
    await run(async () => {
      const page = await api.fetchPurchases(PAGE_SIZE, purchases.length);
      setPurchases(prev => [...prev, ...page.purchases]);
      setPurchasesTotal(page.total);
    });
    setLoading(false);
  }, [run, purchases.length]);

  const handleUnlock = (token: string) => {
    api.setToken(token);
    setAuthError('');
    setAuthed(true);
  };

  const handleSetPro = async (
    user: User,
    isPro: boolean,
    tier: GrantableTier,
  ) => {
    const who = user.name || user.email || user.id;
    if (
      !confirm(
        isPro
          ? `Grant ${who} a ${tier} Pro entitlement?`
          : `Revoke Pro from ${who}?`,
      )
    ) {
      return;
    }
    setPendingUserId(user.id);
    const res = await run(() =>
      api.setUserPro(user.id, isPro, isPro ? tier : 'free'),
    );
    setPendingUserId(null);
    if (res) {
      setNotice(
        isPro ? `${who} now holds a ${tier} entitlement.` : `Pro revoked from ${who}.`,
      );
      await reloadUsers();
    }
  };

  const handleCreatePreset = async (preset: Preset) => {
    setSaving(true);
    const res = await run(() => api.savePreset(preset));
    setSaving(false);
    if (res) {
      setModalOpen(false);
      setNotice(`Preset "${preset.name}" saved.`);
      await reloadPresets();
    }
  };

  const handleDeletePreset = async (preset: Preset) => {
    if (!confirm(`Delete the "${preset.name}" preset from the catalog?`)) return;
    const res = await run(() => api.deletePreset(preset.id));
    if (res) {
      setNotice(`Preset "${preset.name}" deleted.`);
      await reloadPresets();
    }
  };

  const handleSeedDemo = async () => {
    if (
      !confirm(
        'Write three fake users and their subscriptions into this database?\n\n' +
          'This is for empty dev databases. It is skipped automatically if real users exist.',
      )
    ) {
      return;
    }
    const res = await run(() => api.seedDemoData());
    if (res) {
      setNotice(res.message);
      if (res.seeded > 0) await loadAll();
    }
  };

  if (!authed) {
    return <LoginView error={authError} onUnlock={handleUnlock} />;
  }

  const tab = TABS.find(t => t.id === activeTab) ?? TABS[0];

  return (
    <div className="flex min-h-screen bg-[#F2F2F7] text-[#1C1C1E]">
      <aside className="w-64 border-r border-[#E5E5EA] bg-white/80 backdrop-blur-xl flex flex-col justify-between p-4 fixed h-full z-20">
        <div>
          <div className="flex items-center gap-3 px-3 py-4 mb-4">
            <div className="w-10 h-10 rounded-xl bg-[#5856D6] flex items-center justify-center text-white shadow-sm font-bold text-xl">
              S
            </div>
            <div>
              <h1 className="font-bold text-base tracking-tight leading-tight">
                SubFlow
              </h1>
              <div className="flex items-center gap-1.5 mt-0.5">
                <span className="w-2 h-2 rounded-full bg-[#34C759]" />
                <span className="text-xs text-[#8E8E93] font-medium">
                  Console v1.0
                </span>
              </div>
            </div>
          </div>

          <nav className="space-y-1" aria-label="Console sections">
            {TABS.map(item => {
              const Icon = item.icon;
              const isActive = activeTab === item.id;
              // Counts come from the KPI endpoint, which reports whole-table
              // totals. They used to be the length of the loaded page, so a
              // server with 400 users showed "50" here.
              const badge =
                item.id === 'presets'
                  ? presets.length
                  : item.id === 'users'
                    ? usersTotal
                    : item.id === 'revenue'
                      ? purchasesTotal
                      : undefined;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setActiveTab(item.id)}
                  aria-current={isActive ? 'page' : undefined}
                  className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl font-medium text-sm transition-all ${
                    isActive
                      ? 'bg-[#5856D6] text-white shadow-sm'
                      : 'text-[#5D5E63] hover:bg-[#F2F2F7] hover:text-[#1C1C1E]'
                  }`}
                >
                  <span className="flex items-center gap-2.5">
                    <Icon className="w-4 h-4" />
                    {item.label}
                  </span>
                  {badge !== undefined && (
                    <span
                      className={`text-xs px-2 py-0.5 rounded-full font-semibold tabular-nums ${
                        isActive
                          ? 'bg-white/20 text-white'
                          : 'bg-[#E5E5EA] text-[#5D5E63]'
                      }`}
                    >
                      {badge}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        <div className="border-t border-[#E5E5EA] pt-4 space-y-2">
          <button
            type="button"
            onClick={handleSeedDemo}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl bg-[#F2F2F7] text-xs font-semibold text-[#5856D6] hover:bg-[#E5E5EA] transition"
          >
            <Sparkles className="w-3.5 h-3.5" />
            Seed demo data
          </button>
          <button
            type="button"
            onClick={lock}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-xl bg-[#F2F2F7] text-xs font-semibold text-[#8E8E93] hover:bg-[#E5E5EA] transition"
          >
            <Lock className="w-3.5 h-3.5" />
            Lock console
          </button>
        </div>
      </aside>

      <main className="ml-64 flex-1 p-8 min-w-0">
        <div className="flex items-start justify-between gap-4 mb-8">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">{tab.heading}</h2>
            <p className="text-sm text-[#8E8E93] mt-0.5">
              {lastLoaded
                ? `Updated ${lastLoaded.toLocaleTimeString()}`
                : 'Loading…'}
            </p>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            <button
              type="button"
              onClick={() => void loadAll()}
              disabled={loading}
              className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-[#E5E5EA] text-sm font-medium hover:bg-[#F2F2F7] disabled:opacity-50 transition shadow-xs"
            >
              <RefreshCw
                className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`}
              />
              Refresh
            </button>
            {activeTab === 'presets' && (
              <button
                type="button"
                onClick={() => setModalOpen(true)}
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-[#5856D6] text-white text-sm font-medium hover:bg-[#4745B8] transition shadow-sm"
              >
                <Plus className="w-4 h-4" />
                Add preset
              </button>
            )}
          </div>
        </div>

        {error && (
          <Banner tone="error" message={error} onDismiss={() => setError('')} />
        )}
        {notice && (
          <Banner
            tone="success"
            message={notice}
            onDismiss={() => setNotice('')}
          />
        )}

        {activeTab === 'overview' && <OverviewView kpi={kpi} />}
        {activeTab === 'presets' && (
          <PresetsView
            presets={presets}
            loading={loading}
            onDelete={handleDeletePreset}
          />
        )}
        {activeTab === 'users' && (
          <UsersView
            users={users}
            total={usersTotal}
            loading={loading}
            onLoadMore={() => void loadMoreUsers()}
            onSetPro={handleSetPro}
            pendingUserId={pendingUserId}
          />
        )}
        {activeTab === 'revenue' && (
          <RevenueView
            revenue={revenue}
            purchases={purchases}
            total={purchasesTotal}
            loading={loading}
            onLoadMore={() => void loadMorePurchases()}
          />
        )}
        {activeTab === 'rates' && (
          <RatesView
            base={rates.base}
            rates={rates.rates}
            updatedAt={rates.updatedAt}
          />
        )}
      </main>

      {modalOpen && (
        <PresetFormModal
          saving={saving}
          onClose={() => setModalOpen(false)}
          onSubmit={handleCreatePreset}
        />
      )}
    </div>
  );
}

export default App;
