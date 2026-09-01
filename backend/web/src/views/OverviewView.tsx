import { Boxes, DollarSign, ShieldCheck, TrendingUp, Users } from 'lucide-react';
import {
  Card,
  EmptyState,
  SectionHeading,
  Skeleton,
  StatCard,
  Th,
  tableClass,
} from '../components/ui';
import type { KPI } from '../types';

const money = (n: number) =>
  n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });

export function OverviewView({ kpi }: { kpi: KPI | null }) {
  if (!kpi) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {[0, 1, 2, 3].map(i => (
            <Card key={i} className="p-5">
              <Skeleton rows={1} />
            </Card>
          ))}
        </div>
        <Card className="p-6">
          <Skeleton rows={4} />
        </Card>
      </div>
    );
  }

  const avgSubs =
    kpi.total_users > 0
      ? (kpi.total_tracked_subs / kpi.total_users).toFixed(1)
      : '0.0';

  // Bars are scaled against the most-tracked service rather than a flat
  // multiplier. The previous `percentage * 2` capped at 100 meant anything at
  // or above 50% drew a full bar, so the top two entries looked identical.
  const peak = Math.max(1, ...kpi.top_tracked_services.map(s => s.percentage));

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Total Users"
          value={kpi.total_users.toLocaleString()}
          icon={<Users className="w-4 h-4 text-[#5856D6]" />}
          hintTone={kpi.dau > 0 ? 'positive' : 'muted'}
          hint={
            <span className="flex items-center gap-2">
              <TrendingUp className="w-3 h-3" />
              DAU {kpi.dau} · MAU {kpi.mau}
            </span>
          }
        />
        <StatCard
          label="Tracked Subscriptions"
          value={kpi.total_tracked_subs.toLocaleString()}
          icon={<Boxes className="w-4 h-4 text-[#FF9500]" />}
          hint={`Avg ${avgSubs} subs / user`}
        />
        <StatCard
          label="Pro Conversion"
          value={`${kpi.pro_conversion_rate.toFixed(1)}%`}
          icon={<ShieldCheck className="w-4 h-4 text-[#34C759]" />}
          hintTone="accent"
          hint={`${kpi.pro_subscribers} accounts hold a Pro entitlement`}
        />
        <StatCard
          label="Estimated MRR"
          value={money(kpi.estimated_mrr)}
          icon={<DollarSign className="w-4 h-4 text-[#34C759]" />}
          hint={`${money(kpi.estimated_arr)} annualised · from the purchase ledger`}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="p-6">
          <SectionHeading
            title="Top Tracked Subscriptions"
            subtitle="Share of all tracked subscription rows."
          />
          {kpi.top_tracked_services.length === 0 ? (
            <EmptyState
              title="Nothing tracked yet"
              detail="These appear once users sync subscriptions to this server."
            />
          ) : (
            <div className="space-y-4">
              {kpi.top_tracked_services.map((item, idx) => (
                <div key={item.name} className="space-y-1.5">
                  <div className="flex justify-between items-center text-sm font-medium gap-3">
                    <span className="flex items-center gap-2 min-w-0">
                      <span className="w-5 text-xs text-[#8E8E93] font-mono font-bold shrink-0">
                        #{idx + 1}
                      </span>
                      <span className="truncate">{item.name}</span>
                    </span>
                    <span className="font-mono text-xs font-bold text-[#5856D6] shrink-0">
                      {item.count} · {item.percentage.toFixed(1)}%
                    </span>
                  </div>
                  <div className="w-full h-2 bg-[#F2F2F7] rounded-full overflow-hidden">
                    <div
                      className="h-full bg-[#5856D6] rounded-full transition-all duration-500"
                      style={{ width: `${(item.percentage / peak) * 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card className="p-6">
          <SectionHeading title="Expense Category Breakdown" />
          {Object.keys(kpi.category_distribution).length === 0 ? (
            <EmptyState title="No category data yet" />
          ) : (
            <div className="grid grid-cols-2 gap-3">
              {Object.entries(kpi.category_distribution)
                .sort(([, a], [, b]) => b - a)
                .map(([cat, count]) => (
                  <div
                    key={cat}
                    className="p-3.5 rounded-xl bg-[#F8F8FA] border border-[#E5E5EA]"
                  >
                    <div className="text-xs text-[#8E8E93] font-medium truncate">
                      {cat}
                    </div>
                    <div className="text-2xl font-bold font-mono tabular-nums mt-1">
                      {count}
                    </div>
                  </div>
                ))}
            </div>
          )}
        </Card>
      </div>

      <Card className="p-6">
        <SectionHeading
          title="Last 7 Days"
          subtitle="Signups and purchases actually recorded on each day."
        />
        <div className="overflow-x-auto">
          <table className={tableClass}>
            <thead>
              <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs">
                <Th>Date</Th>
                <Th align="right">New users</Th>
                <Th align="right">New purchases</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E5E5EA] font-mono text-xs tabular-nums">
              {kpi.user_growth_trend.map(pt => (
                <tr key={pt.date} className="hover:bg-[#F8F8FA] transition">
                  <td className="py-3 font-sans font-medium text-sm">
                    {pt.date}
                  </td>
                  <td
                    className={`py-3 text-right ${pt.new_users > 0 ? 'text-[#34C759] font-bold' : 'text-[#C7C7CC]'}`}
                  >
                    {pt.new_users > 0 ? `+${pt.new_users}` : '0'}
                  </td>
                  <td
                    className={`py-3 text-right ${pt.new_purchases > 0 ? 'text-[#5856D6] font-bold' : 'text-[#C7C7CC]'}`}
                  >
                    {pt.new_purchases > 0 ? `+${pt.new_purchases}` : '0'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
}
