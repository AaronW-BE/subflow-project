import {
  Card,
  EmptyState,
  PageFooter,
  SectionHeading,
  Skeleton,
  StatCard,
  Th,
  tableClass,
} from '../components/ui';
import type { Purchase, Revenue } from '../types';

const money = (n: number) =>
  n.toLocaleString('en-US', { style: 'currency', currency: 'USD' });

export function RevenueView({
  revenue,
  purchases,
  total,
  loading,
  onLoadMore,
}: {
  revenue: Revenue | null;
  purchases: Purchase[];
  total: number;
  loading: boolean;
  onLoadMore: () => void;
}) {
  if (!revenue) {
    return (
      <Card className="p-6">
        <Skeleton rows={5} />
      </Card>
    );
  }

  const tiers = Object.entries(revenue.purchases_by_tier ?? {}).sort(
    ([, a], [, b]) => b - a,
  );

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Estimated MRR"
          value={money(revenue.estimated_mrr)}
          hint="Monthly + annual subscriptions at US list price"
        />
        <StatCard
          label="Estimated ARR"
          value={money(revenue.estimated_arr)}
          hint="MRR × 12"
        />
        <StatCard
          label="Lifetime Sales"
          value={revenue.lifetime_sales.toLocaleString()}
          hint={`${money(revenue.lifetime_gross)} gross · excluded from MRR`}
        />
        <StatCard
          label="Last 30 Days"
          value={revenue.purchases_last_30_days.toLocaleString()}
          hint="New purchases reported by clients"
        />
      </div>

      {tiers.length > 0 && (
        <Card className="p-6">
          <SectionHeading title="Purchases by Tier" />
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {tiers.map(([tier, count]) => (
              <div
                key={tier}
                className="p-3.5 rounded-xl bg-[#F8F8FA] border border-[#E5E5EA]"
              >
                <div className="text-xs text-[#8E8E93] font-medium capitalize">
                  {tier}
                </div>
                <div className="text-2xl font-bold font-mono tabular-nums mt-1">
                  {count}
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card className="p-6">
        <SectionHeading
          title="Purchase Ledger"
          subtitle="Purchase tokens reported by clients. Amounts above are estimates from US list prices; settled revenue comes from the Play Console payout reports."
        />

        {purchases.length === 0 ? (
          <EmptyState
            title="No purchases recorded yet"
            detail="A row lands here when a client reports a Play purchase token to POST /billing/purchase."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className={tableClass}>
              <thead>
                <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs">
                  <Th>Product</Th>
                  <Th>Tier</Th>
                  <Th>State</Th>
                  <Th>User</Th>
                  <Th>Order</Th>
                  <Th align="right">Reported</Th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E5E5EA]">
                {purchases.map(p => (
                  <tr
                    key={p.purchase_token}
                    className="hover:bg-[#F8F8FA] transition"
                  >
                    <td className="py-3 font-medium text-xs font-mono">
                      {p.product_id}
                    </td>
                    <td className="py-3">
                      <span className="text-xs px-2 py-0.5 rounded-full font-semibold bg-[#EEEEFB] text-[#5856D6] capitalize">
                        {p.pro_tier}
                      </span>
                    </td>
                    <td className="py-3 text-xs text-[#5D5E63] capitalize">
                      {p.state}
                    </td>
                    <td className="py-3 font-mono text-xs text-[#5D5E63]">
                      {p.user_id}
                    </td>
                    <td className="py-3 font-mono text-xs text-[#5D5E63]">
                      {p.order_id || '—'}
                    </td>
                    <td className="py-3 text-xs text-[#8E8E93] text-right whitespace-nowrap">
                      {new Date(p.reported_at).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <PageFooter
          shown={purchases.length}
          total={total}
          noun="purchases"
          loading={loading}
          onLoadMore={onLoadMore}
        />
      </Card>
    </div>
  );
}
