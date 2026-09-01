import { Card, SectionHeading, Skeleton } from '../components/ui';

/**
 * Rates span four orders of magnitude (EUR 0.92 to KRW 1370), so a fixed two
 * decimals either wasted precision on the large ones or hid it on the small.
 * Precision scales with magnitude, and never below what the exact value needs —
 * rounding JPY to "155" directly above "1 USD = 155.3 JPY" reads as a bug.
 */
function formatRate(v: number): string {
  const digits = v >= 100 ? 1 : v >= 1 ? 2 : 4;
  return v.toLocaleString('en-US', { maximumFractionDigits: digits });
}

export function RatesView({
  base,
  rates,
  updatedAt,
}: {
  base: string;
  rates: Record<string, number>;
  updatedAt: string | null;
}) {
  const entries = Object.entries(rates).sort(([a], [b]) => a.localeCompare(b));

  return (
    <Card className="p-6">
      <SectionHeading
        title={`Base currency: ${base}`}
        subtitle={
          updatedAt
            ? `Held in memory by the Go process and served to clients for conversion. Loaded ${new Date(updatedAt).toLocaleString()}; this is a static table, not a live feed.`
            : 'Held in memory by the Go process and served to clients for conversion.'
        }
      />

      {entries.length === 0 ? (
        <Skeleton rows={2} />
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {entries.map(([currency, value]) => (
            <div
              key={currency}
              className="p-4 rounded-xl border border-[#E5E5EA] bg-[#F8F8FA]"
            >
              <div className="text-xs font-bold text-[#8E8E93]">{currency}</div>
              <div className="text-2xl font-bold font-mono tabular-nums mt-1">
                {formatRate(value)}
              </div>
              <div className="text-xs text-[#5D5E63] mt-1">
                1 {base} = {value} {currency}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
