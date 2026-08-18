import React, { useState } from 'react';
import { Store, ChevronDown, MapPin } from 'lucide-react';
import { Restaurant } from '../types';

interface RestaurantPickerProps {
  restaurants: Restaurant[];
  currentRestaurantId: string | null;
  onSelect: (id: string) => void;
}

export const RestaurantPicker: React.FC<RestaurantPickerProps> = ({
  restaurants,
  currentRestaurantId,
  onSelect,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const current = restaurants.find((r) => r.id === currentRestaurantId);

  if (restaurants.length === 0) return null;

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen((v) => !v)}
        className="flex items-center gap-2 py-1.5 pl-2.5 pr-3 rounded-xl bg-stone-900 border border-stone-800 hover:border-amber-500/40 text-stone-200 text-xs transition-all cursor-pointer shadow-sm max-w-[180px]"
      >
        <Store className="w-4 h-4 text-amber-400 shrink-0" />
        <span className="truncate font-medium">{current?.name || 'Select Restaurant'}</span>
        <ChevronDown className={`w-3.5 h-3.5 text-stone-500 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)} />
          <div className="absolute left-0 mt-2 w-72 bg-stone-900 border border-stone-700 rounded-2xl shadow-2xl z-50 overflow-hidden">
            <div className="p-3 border-b border-stone-800">
              <p className="text-[10px] font-mono uppercase tracking-widest text-stone-500">
                Choose a restaurant
              </p>
            </div>
            {restaurants.map((r) => (
              <button
                key={r.id}
                onClick={() => {
                  onSelect(r.id);
                  setIsOpen(false);
                }}
                className={`w-full flex items-start gap-3 p-3 text-left transition-colors hover:bg-stone-800/60 cursor-pointer border-b border-stone-800/50 last:border-0 ${
                  r.id === currentRestaurantId ? 'bg-amber-500/[0.06]' : ''
                }`}
              >
                <div className="w-9 h-9 rounded-xl overflow-hidden bg-stone-800 shrink-0 flex items-center justify-center">
                  {r.logoUrl ? (
                    <img src={r.logoUrl} alt={r.name} className="w-full h-full object-cover" />
                  ) : (
                    <Store className="w-4 h-4 text-amber-400" />
                  )}
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-bold text-stone-100 truncate">{r.name}</p>
                  <p className="text-[10px] text-stone-500 flex items-center gap-1 mt-0.5">
                    <MapPin className="w-3 h-3" />
                    {r.city || '—'} · {r.cuisine || 'Multi-cuisine'}
                  </p>
                </div>
                {r.id === currentRestaurantId && (
                  <span className="ml-auto w-2 h-2 rounded-full bg-amber-400 shrink-0 mt-2" />
                )}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
};
