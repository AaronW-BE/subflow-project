import { useMemo, useState } from 'react';
import { ExternalLink, Search, Sparkles, Trash2 } from 'lucide-react';
import { Card, EmptyState, Th, fieldClass, tableClass } from '../components/ui';
import { PRESET_CATEGORIES, type Preset } from '../types';

export function PresetsView({
  presets,
  loading,
  onDelete,
}: {
  presets: Preset[];
  loading: boolean;
  onDelete: (preset: Preset) => void;
}) {
  const [category, setCategory] = useState('All');
  const [query, setQuery] = useState('');

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return presets.filter(p => {
      const matchesCat = category === 'All' || p.category === category;
      // Match the id too: it is the key the app looks presets up by, so being
      // able to search for one is how you confirm a preset actually exists.
      const matchesSearch =
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.id.toLowerCase().includes(q);
      return matchesCat && matchesSearch;
    });
  }, [presets, category, query]);

  return (
    <Card className="p-6 space-y-4">
      <div className="flex flex-col sm:flex-row justify-between gap-4">
        <div className="flex gap-2 overflow-x-auto pb-1">
          {['All', ...PRESET_CATEGORIES].map(cat => (
            <button
              key={cat}
              type="button"
              onClick={() => setCategory(cat)}
              aria-pressed={category === cat}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition ${
                category === cat
                  ? 'bg-[#5856D6] text-white'
                  : 'bg-[#F2F2F7] text-[#5D5E63] hover:bg-[#E5E5EA]'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>
        <div className="relative shrink-0">
          <Search className="w-4 h-4 absolute left-3 top-2.5 text-[#8E8E93] pointer-events-none" />
          <label htmlFor="preset-search" className="sr-only">
            Search presets
          </label>
          <input
            id="preset-search"
            type="search"
            placeholder={`Search ${presets.length} presets…`}
            value={query}
            onChange={e => setQuery(e.target.value)}
            className={`${fieldClass} pl-9 py-1.5 text-xs sm:w-64`}
          />
        </div>
      </div>

      {filtered.length === 0 ? (
        <EmptyState
          title={loading ? 'Loading presets…' : 'No presets match this filter'}
          detail={
            loading
              ? undefined
              : presets.length === 0
                ? 'The catalogue is seeded on first boot. An empty table means the presets table failed to populate.'
                : 'Clear the search or pick a different category.'
          }
        />
      ) : (
        <div className="overflow-x-auto">
          <table className={tableClass}>
            <thead>
              <tr className="border-b border-[#E5E5EA] text-[#8E8E93] text-xs">
                <Th>Service</Th>
                <Th>Category</Th>
                <Th>Default cycle</Th>
                <Th align="right">Default price</Th>
                <Th>Status</Th>
                <Th align="right">Actions</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E5E5EA]">
              {filtered.map(preset => (
                <tr key={preset.id} className="hover:bg-[#F8F8FA] transition">
                  <td className="py-3">
                    <div className="flex items-center gap-3">
                      <div
                        aria-hidden="true"
                        className="w-8 h-8 rounded-lg flex items-center justify-center font-bold text-white text-xs shadow-xs shrink-0"
                        style={{ backgroundColor: preset.brand_color || '#5856D6' }}
                      >
                        {preset.name.charAt(0)}
                      </div>
                      <div className="min-w-0">
                        <div className="font-semibold text-sm truncate">
                          {preset.name}
                        </div>
                        {preset.website_url ? (
                          <a
                            href={preset.website_url}
                            target="_blank"
                            rel="noreferrer"
                            className="text-xs text-[#8E8E93] hover:text-[#5856D6] flex items-center gap-1"
                          >
                            website <ExternalLink className="w-2.5 h-2.5" />
                          </a>
                        ) : (
                          <div className="text-xs text-[#C7C7CC] font-mono truncate">
                            {preset.id}
                          </div>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="py-3">
                    <span className="px-2.5 py-1 rounded-full text-xs font-medium bg-[#F2F2F7] text-[#5D5E63]">
                      {preset.category}
                    </span>
                  </td>
                  <td className="py-3 text-xs capitalize font-medium">
                    {preset.default_cycle}
                  </td>
                  <td className="py-3 font-mono font-semibold tabular-nums text-right">
                    ${preset.default_amount_usd.toFixed(2)}
                  </td>
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
                      type="button"
                      onClick={() => onDelete(preset)}
                      aria-label={`Delete the ${preset.name} preset`}
                      className="p-1.5 text-[#FF3B30] hover:bg-[#FFF1F0] rounded-lg transition"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {filtered.length > 0 && filtered.length !== presets.length && (
        <p className="text-xs text-[#8E8E93] pt-2 border-t border-[#E5E5EA]">
          Showing {filtered.length} of {presets.length} presets.
        </p>
      )}
    </Card>
  );
}
