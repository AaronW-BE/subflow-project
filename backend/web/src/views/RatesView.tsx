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
  provider,
  providerUrl,
}: {
  base: string;
  rates: Record<string, number>;
  updatedAt: string | null;
  provider: string;
  providerUrl: string;
}) {
  const entries = Object.entries(rates).sort(([a], [b]) => a.localeCompare(b));

  return (
    <Card className="p-6">
      <SectionHeading
        title={`Base currency: ${base}`}
        subtitle={
          provider
            ? `Refreshed daily from ${provider} and served to clients for conversion. Quoted ${updatedAt ? new Date(updatedAt).toLocaleString() : 'unknown'} — that is the provider's own quote time, not when this server fetched it.`
            : 'Built-in fallback table — no provider data has been fetched yet. These values are approximate and known to be stale.'
        }
      />

      {!provider && (
        <div className="mb-5 px-4 py-3 rounded-xl border border-[#FFE2B8] bg-[#FFF8EC] text-[#8A5A00] text-sm leading-relaxed">
          Serving the compile-time fallback table. Measured against live data,
          25 of its 40 rates are off by more than 5%. Check the server log for
          the reason the refresh has not succeeded.
        </div>
      )}

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

      {provider && providerUrl && (
        <p className="mt-6 pt-4 border-t border-[#E5E5EA] text-xs text-[#8E8E93]">
          {/* The feed's terms require this credit wherever the rates appear. */}
          Rates By{' '}
          <a
            href={providerUrl}
            target="_blank"
            rel="noreferrer"
            className="text-[#5856D6] font-medium hover:underline"
          >
            {provider}
          </a>
        </p>
      )}
    </Card>
  );
}
