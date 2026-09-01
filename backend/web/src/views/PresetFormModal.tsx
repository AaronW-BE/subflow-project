import { useState, type FormEvent } from 'react';
import { Field, Modal, fieldClass } from '../components/ui';
import { PRESET_CATEGORIES, type Preset } from '../types';

const EMPTY: Preset = {
  id: '',
  name: '',
  category: 'Entertainment',
  brand_color: '#5856D6',
  icon_url: '',
  default_cycle: 'monthly',
  default_amount_usd: 9.99,
  website_url: '',
  is_popular: true,
};

/** Slug used when the operator leaves the id blank. */
const slugify = (name: string) =>
  name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');

export function PresetFormModal({
  onClose,
  onSubmit,
  saving,
}: {
  onClose: () => void;
  onSubmit: (preset: Preset) => Promise<void>;
  saving: boolean;
}) {
  const [preset, setPreset] = useState<Preset>(EMPTY);

  const id = preset.id.trim() || slugify(preset.name);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!id) return;
    await onSubmit({
      ...preset,
      id,
      name: preset.name.trim(),
      website_url: preset.website_url.trim(),
      default_amount_usd: Number(preset.default_amount_usd) || 0,
    });
  };

  const set = <K extends keyof Preset>(key: K, value: Preset[K]) =>
    setPreset(p => ({ ...p, [key]: value }));

  return (
    <Modal title="Create subscription preset" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4 text-sm">
        <Field label="Service name" htmlFor="preset-name">
          <input
            id="preset-name"
            required
            autoFocus
            type="text"
            placeholder="e.g. Paramount+"
            value={preset.name}
            onChange={e => set('name', e.target.value)}
            className={fieldClass}
          />
          {/* The id is the primary key and cannot be changed later, so show
              what is about to be written rather than deriving it invisibly. */}
          {id && (
            <p className="text-xs text-[#8E8E93] mt-1.5 font-mono">id: {id}</p>
          )}
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Category" htmlFor="preset-category">
            <select
              id="preset-category"
              value={preset.category}
              onChange={e => set('category', e.target.value)}
              className={fieldClass}
            >
              {PRESET_CATEGORIES.map(c => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Brand colour" htmlFor="preset-color">
            <input
              id="preset-color"
              type="color"
              value={preset.brand_color}
              onChange={e => set('brand_color', e.target.value)}
              className="w-full h-9 p-1 bg-[#F2F2F7] border border-[#E5E5EA] rounded-xl cursor-pointer"
            />
          </Field>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Default cycle" htmlFor="preset-cycle">
            <select
              id="preset-cycle"
              value={preset.default_cycle}
              onChange={e => set('default_cycle', e.target.value)}
              className={fieldClass}
            >
              <option value="weekly">Weekly</option>
              <option value="monthly">Monthly</option>
              <option value="annually">Annually</option>
            </select>
          </Field>
          <Field label="Price (USD)" htmlFor="preset-price">
            <input
              id="preset-price"
              required
              type="number"
              step="0.01"
              min="0"
              value={preset.default_amount_usd}
              onChange={e =>
                set('default_amount_usd', parseFloat(e.target.value) || 0)
              }
              className={fieldClass}
            />
          </Field>
        </div>

        <Field label="Website URL" htmlFor="preset-url">
          <input
            id="preset-url"
            type="url"
            placeholder="https://…"
            value={preset.website_url}
            onChange={e => set('website_url', e.target.value)}
            className={fieldClass}
          />
        </Field>

        <div className="flex items-center gap-2 pt-2">
          <input
            type="checkbox"
            id="preset-popular"
            checked={preset.is_popular}
            onChange={e => set('is_popular', e.target.checked)}
            className="accent-[#5856D6]"
          />
          <label
            htmlFor="preset-popular"
            className="text-xs font-medium cursor-pointer"
          >
            Feature in the app's Popular services list
          </label>
        </div>

        <div className="flex justify-end gap-2 pt-4">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-[#5D5E63] hover:bg-[#F2F2F7] transition"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={saving || !id}
            className="px-4 py-2 rounded-xl text-xs font-semibold bg-[#5856D6] text-white hover:bg-[#4745B8] disabled:opacity-40 transition"
          >
            {saving ? 'Saving…' : 'Save preset'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
