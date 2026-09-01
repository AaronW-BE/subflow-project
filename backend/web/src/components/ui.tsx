import { useEffect, type ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, X } from 'lucide-react';

export function Card({
  children,
  className = '',
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={`bg-white rounded-2xl border border-[#E5E5EA] shadow-xs ${className}`}
    >
      {children}
    </div>
  );
}

export function StatCard({
  label,
  value,
  hint,
  icon,
  hintTone = 'muted',
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  icon?: ReactNode;
  hintTone?: 'muted' | 'positive' | 'accent';
}) {
  const tone = {
    muted: 'text-[#8E8E93]',
    positive: 'text-[#34C759]',
    accent: 'text-[#5856D6]',
  }[hintTone];

  return (
    <Card className="p-5">
      <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-[#8E8E93] mb-2">
        <span>{label}</span>
        {icon}
      </div>
      <div className="text-3xl font-bold tracking-tight font-mono tabular-nums">
        {value}
      </div>
      {hint && <div className={`mt-2 text-xs font-medium ${tone}`}>{hint}</div>}
    </Card>
  );
}

export function SectionHeading({
  title,
  subtitle,
}: {
  title: string;
  subtitle?: string;
}) {
  return (
    <div className="mb-4">
      <h3 className="font-bold text-base">{title}</h3>
      {subtitle && (
        <p className="text-xs text-[#8E8E93] mt-0.5 leading-relaxed">
          {subtitle}
        </p>
      )}
    </div>
  );
}

/** Column header. `scope="col"` is what lets a screen reader announce the cell's column. */
export function Th({
  children,
  align = 'left',
  className = '',
}: {
  children: ReactNode;
  align?: 'left' | 'right';
  className?: string;
}) {
  return (
    <th
      scope="col"
      className={`pb-3 font-semibold ${align === 'right' ? 'text-right' : 'text-left'} ${className}`}
    >
      {children}
    </th>
  );
}

export function EmptyState({
  title,
  detail,
}: {
  title: string;
  detail?: string;
}) {
  return (
    <div className="px-6 py-12 text-center">
      <p className="text-sm font-medium text-[#5D5E63]">{title}</p>
      {detail && (
        <p className="text-xs text-[#8E8E93] mt-1.5 max-w-sm mx-auto leading-relaxed">
          {detail}
        </p>
      )}
    </div>
  );
}

/** Grey blocks standing in for content that has not arrived yet. */
export function Skeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div className="space-y-3" aria-hidden="true">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-12 rounded-xl bg-[#EFEFF4] animate-pulse" />
      ))}
    </div>
  );
}

export function Banner({
  tone,
  message,
  onDismiss,
}: {
  tone: 'error' | 'success';
  message: string;
  onDismiss: () => void;
}) {
  const isError = tone === 'error';
  return (
    <div
      role={isError ? 'alert' : 'status'}
      className={`flex items-start gap-3 px-4 py-3 rounded-xl border text-sm mb-6 ${
        isError
          ? 'bg-[#FFF1F0] border-[#FFD6D2] text-[#B3261E]'
          : 'bg-[#EEFBF1] border-[#CBEFD6] text-[#1D7A38]'
      }`}
    >
      {isError ? (
        <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
      ) : (
        <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
      )}
      <span className="flex-1 leading-relaxed">{message}</span>
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss message"
        className="p-0.5 rounded hover:bg-black/5 transition"
      >
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
}

/**
 * "Showing 50 of 900" plus the control to fetch the next page.
 *
 * Every table in the console is paginated server-side. Without this the first
 * page reads as the complete table, which is how the sidebar came to show a
 * user count of 50 next to an Overview card reporting several hundred.
 */
export function PageFooter({
  shown,
  total,
  noun,
  loading,
  onLoadMore,
}: {
  shown: number;
  total: number;
  noun: string;
  loading: boolean;
  onLoadMore: () => void;
}) {
  if (total === 0) return null;
  const complete = shown >= total;

  return (
    <div className="flex items-center justify-between gap-4 pt-4 mt-2 border-t border-[#E5E5EA]">
      <p className="text-xs text-[#8E8E93]">
        {complete ? (
          <>
            All <span className="font-semibold text-[#5D5E63]">{total}</span>{' '}
            {noun}
          </>
        ) : (
          <>
            Showing <span className="font-semibold text-[#5D5E63]">{shown}</span>{' '}
            of <span className="font-semibold text-[#5D5E63]">{total}</span>{' '}
            {noun}
          </>
        )}
      </p>
      {!complete && (
        <button
          type="button"
          onClick={onLoadMore}
          disabled={loading}
          className="px-3.5 py-1.5 rounded-xl bg-[#F2F2F7] text-xs font-semibold text-[#5856D6] hover:bg-[#E5E5EA] disabled:opacity-40 transition"
        >
          {loading ? 'Loading…' : 'Load more'}
        </button>
      )}
    </div>
  );
}

/** Modal shell: closes on Escape and on a backdrop click, and traps nothing else. */
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 bg-black/40 backdrop-blur-xs flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={e => e.stopPropagation()}
        className="bg-white w-full max-w-md rounded-2xl p-6 shadow-xl border border-[#E5E5EA] max-h-[90vh] overflow-y-auto"
      >
        <h3 className="text-lg font-bold mb-4">{title}</h3>
        {children}
      </div>
    </div>
  );
}

/**
 * Shared table styling.
 *
 * The horizontal gutters matter: with plain `py-3` cells and no padding, a
 * right-aligned column sits flush against whatever follows it — the preset
 * price ran straight into the "Popular" badge, and the two headers rendered as
 * one word. Edge cells stay flush with the card so the table still lines up.
 */
export const tableClass =
  'w-full text-left text-sm ' +
  '[&_th]:px-3 [&_td]:px-3 ' +
  '[&_th:first-child]:pl-0 [&_td:first-child]:pl-0 ' +
  '[&_th:last-child]:pr-0 [&_td:last-child]:pr-0';

/** Shared input styling, so the six form fields cannot drift apart. */
export const fieldClass =
  'w-full px-3 py-2 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl text-sm ' +
  'outline-none focus:border-[#5856D6] focus:ring-2 focus:ring-[#5856D6]/30 transition';

export function Field({
  label,
  children,
  htmlFor,
}: {
  label: string;
  children: ReactNode;
  htmlFor: string;
}) {
  return (
    <div>
      <label
        htmlFor={htmlFor}
        className="block text-xs font-semibold text-[#8E8E93] uppercase mb-1"
      >
        {label}
      </label>
      {children}
    </div>
  );
}
