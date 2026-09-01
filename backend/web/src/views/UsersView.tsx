import { useMemo, useState } from 'react';
import { CheckCircle2, Search } from 'lucide-react';
import {
  Card,
  EmptyState,
  PageFooter,
  Skeleton,
  Th,
  fieldClass,
  tableClass,
} from '../components/ui';
import { GRANTABLE_TIERS, type GrantableTier, type User } from '../types';

/** Relative age, so "joined" reads without arithmetic. */
function joined(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return 'unknown';
  const days = Math.floor((Date.now() - then) / 86_400_000);
  if (days <= 0) return 'today';
  if (days === 1) return 'yesterday';
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString();
}

export function UsersView({
  users,
  total,
  loading,
  onLoadMore,
  onSetPro,
  pendingUserId,
}: {
  users: User[];
  total: number;
  loading: boolean;
  onLoadMore: () => void;
  onSetPro: (user: User, isPro: boolean, tier: GrantableTier) => void;
  pendingUserId: string | null;
}) {
  const [query, setQuery] = useState('');
  const [proOnly, setProOnly] = useState(false);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return users.filter(u => {
      if (proOnly && !u.is_pro) return false;
      if (!q) return true;
      return (
        u.email?.toLowerCase().includes(q) ||
        u.name?.toLowerCase().includes(q) ||
        u.id.toLowerCase().includes(q)
      );
    });
  }, [users, query, proOnly]);

  if (loading && users.length === 0) {
    return (
      <Card className="p-6">
        <Skeleton rows={6} />
      </Card>
    );
  }

  return (
    <Card className="p-6 space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-xs font-semibold text-[#5D5E63] cursor-pointer">
          <input
            type="checkbox"
            checked={proOnly}
            onChange={e => setProOnly(e.target.checked)}
            className="accent-[#5856D6]"
          />
          Pro accounts only
        </label>
        <div className="relative shrink-0">
          <Search className="w-4 h-4 absolute left-3 top-2.5 text-[#8E8E93] pointer-events-none" />
          <label htmlFor="user-search" className="sr-only">
            Search loaded users
          </label>
          <input
            id="user-search"
            type="search"
            placeholder="Search name, email or id…"
            value={query}
            onChange={e => setQuery(e.target.value)}
            className={`${fieldClass} pl-9 py-1.5 text-xs sm:w-72`}
          />
        </div>
      </div>

      {/* The search runs over rows already fetched, not the whole table, so say
          so rather than letting an empty result imply the user does not exist. */}
      {users.length < total && (
        <p className="text-xs text-[#8E8E93]">
          Search covers the {users.length} users loaded so far. Load more to
          widen it.
        </p>
      )}

      {filtered.length === 0 ? (
        <EmptyState
          title="No users match"
          detail={
            users.length === 0
              ? 'Nobody has signed in to this server yet.'
              : 'Try a different search, or load more users.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className={tableClass}>
            <thead>
              <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs">
                <Th>User</Th>
                <Th>Provider</Th>
                <Th>Entitlement</Th>
                <Th>Joined</Th>
                <Th align="right">Actions</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E5E5EA]">
              {filtered.map(u => {
                const busy = pendingUserId === u.id;
                return (
                  <tr key={u.id} className="hover:bg-[#F8F8FA] transition">
                    <td className="py-3">
                      <div className="flex items-center gap-3">
                        <div
                          aria-hidden="true"
                          className="w-8 h-8 rounded-full bg-[#E5E5EA] flex items-center justify-center font-bold text-xs shrink-0"
                        >
                          {(u.name || u.email || 'U').charAt(0).toUpperCase()}
                        </div>
                        <div className="min-w-0">
                          <div className="font-semibold text-sm truncate">
                            {u.name || 'Anonymous user'}
                          </div>
                          <div className="text-xs text-[#8E8E93] truncate">
                            {u.email || (
                              <span className="font-mono">{u.id}</span>
                            )}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="py-3">
                      <span className="text-xs px-2 py-0.5 rounded-md bg-[#F2F2F7] font-mono uppercase">
                        {u.auth_provider || 'unknown'}
                      </span>
                    </td>
                    <td className="py-3">
                      {u.is_pro ? (
                        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-[#EEFBF1] text-[#1D7A38]">
                          <CheckCircle2 className="w-3 h-3" />
                          Pro · {u.pro_tier}
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-[#F2F2F7] text-[#8E8E93]">
                          Free
                        </span>
                      )}
                    </td>
                    <td className="py-3 text-xs text-[#8E8E93] whitespace-nowrap">
                      {joined(u.created_at)}
                    </td>
                    <td className="py-3 text-right">
                      {u.is_pro ? (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => onSetPro(u, false, 'monthly')}
                          className="px-3 py-1 rounded-lg text-xs font-semibold bg-[#FFF1F0] text-[#FF3B30] hover:bg-[#FFE1DE] disabled:opacity-40 transition"
                        >
                          {busy ? '…' : 'Revoke Pro'}
                        </button>
                      ) : (
                        // Which tier is granted matters: it is written to the
                        // users row and is what the app reads back. The old
                        // button hard-coded "annual" with no way to say
                        // otherwise.
                        <select
                          aria-label={`Grant Pro to ${u.name || u.email || u.id}`}
                          disabled={busy}
                          value=""
                          onChange={e => {
                            const tier = e.target.value as GrantableTier;
                            if (tier) onSetPro(u, true, tier);
                            e.target.value = '';
                          }}
                          className="px-3 py-1 rounded-lg text-xs font-semibold bg-[#EEEEFB] text-[#5856D6] hover:bg-[#E2E2F8] disabled:opacity-40 transition cursor-pointer outline-none"
                        >
                          <option value="">{busy ? '…' : 'Grant Pro'}</option>
                          {GRANTABLE_TIERS.map(t => (
                            <option key={t} value={t}>
                              {t}
                            </option>
                          ))}
                        </select>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* The footer below counts what has been *fetched*; this line counts
          what the filter left visible. Without it "All 4 users" sits under two
          rows and reads as a miscount rather than a filtered view. */}
      {filtered.length !== users.length && (
        <p className="text-xs text-[#8E8E93]">
          Filter matches {filtered.length} of the {users.length} loaded users.
        </p>
      )}

      <PageFooter
        shown={users.length}
        total={total}
        noun="users loaded"
        loading={loading}
        onLoadMore={onLoadMore}
      />
    </Card>
  );
}
