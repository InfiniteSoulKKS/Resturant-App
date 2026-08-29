import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Package, AlertTriangle, XCircle, CheckCircle2, RefreshCw, Bell, Truck, Volume2 } from 'lucide-react';
import { Ingredient } from '../types';
import { listIngredients, requestIngredientRestock } from '../lib/apiClient';
import { useStockAlert } from '../hooks/useStockAlert';

interface KitchenStockDashboardProps {
  restaurantId: string;
  lowStockAlerts: LowStockAlert[];
}

export interface LowStockAlert {
  orderId?: string;
  orderNumber?: string;
  lowStockIngredients: {
    ingredientId?: string;
    name: string;
    stockQuantity: number;
    reorderLevel: number;
    unit: string;
    severity: 'LOW' | 'DEPLETED';
  }[];
  message: string;
  timestamp?: string;
}

function getStockLevel(ing: Ingredient): 'ok' | 'low' | 'depleted' {
  if (ing.stockQuantity <= 0) return 'depleted';
  // Use lowStockThreshold if set, otherwise fall back to reorderLevel
  const threshold = (ing.lowStockThreshold != null && ing.lowStockThreshold > 0)
    ? ing.lowStockThreshold : ing.reorderLevel;
  if (threshold > 0 && ing.stockQuantity <= threshold) return 'low';
  return 'ok';
}

function getStockBarColor(level: 'ok' | 'low' | 'depleted'): string {
  switch (level) {
    case 'depleted': return 'bg-rose-500 shadow-rose-500/40';
    case 'low': return 'bg-amber-500 shadow-amber-500/40';
    case 'ok': return 'bg-emerald-500 shadow-emerald-500/40';
  }
}

function getStockBgColor(level: 'ok' | 'low' | 'depleted'): string {
  switch (level) {
    case 'depleted': return 'bg-rose-500/10 border-rose-500/30';
    case 'low': return 'bg-amber-500/10 border-amber-500/30';
    case 'ok': return 'bg-emerald-500/5 border-emerald-500/20';
  }
}

function getStockTextColor(level: 'ok' | 'low' | 'depleted'): string {
  switch (level) {
    case 'depleted': return 'text-rose-400';
    case 'low': return 'text-amber-400';
    case 'ok': return 'text-emerald-400';
  }
}

function getStockLabel(level: 'ok' | 'low' | 'depleted'): string {
  switch (level) {
    case 'depleted': return 'OUT OF STOCK';
    case 'low': return 'LOW STOCK';
    case 'ok': return 'IN STOCK';
  }
}

export const KitchenStockDashboard: React.FC<KitchenStockDashboardProps> = ({ restaurantId, lowStockAlerts }) => {
  const [ingredients, setIngredients] = useState<Ingredient[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'low' | 'depleted' | 'ok'>('all');
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());
  const [restockingId, setRestockingId] = useState<string | null>(null);
  const [restockMessage, setRestockMessage] = useState<{ id: string; text: string } | null>(null);
  const [alertSoundEnabled, setAlertSoundEnabled] = useState(true);
  const prevDepletedCountRef = useRef<number>(0);
  const { checkAndAlert, playAlertSound, vibrate } = useStockAlert();

  const loadIngredients = useCallback(async () => {
    try {
      const ing = await listIngredients(restaurantId);
      const active = ing.filter(i => i.active !== false);
      setIngredients(active);
      setLastRefresh(new Date());

      // Check for new depleted ingredients and fire alert
      if (alertSoundEnabled) {
        const depletedNow = active.filter(i => i.stockQuantity <= 0).length;
        checkAndAlert(depletedNow, prevDepletedCountRef.current);
        prevDepletedCountRef.current = depletedNow;
      }
    } catch {
      // silent fail
    } finally {
      setIsLoading(false);
    }
  }, [restaurantId, alertSoundEnabled, checkAndAlert]);

  useEffect(() => {
    loadIngredients();
  }, [loadIngredients]);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    const interval = setInterval(loadIngredients, 30_000);
    return () => clearInterval(interval);
  }, [loadIngredients]);

  const filtered = ingredients.filter(ing => {
    const level = getStockLevel(ing);
    if (filter === 'all') return true;
    return level === filter;
  });

  const depletedCount = ingredients.filter(i => getStockLevel(i) === 'depleted').length;
  const lowCount = ingredients.filter(i => getStockLevel(i) === 'low').length;
  const okCount = ingredients.filter(i => getStockLevel(i) === 'ok').length;

  const handleRestock = async (ingredientId: string) => {
    setRestockingId(ingredientId);
    setRestockMessage(null);
    try {
      const msg = await requestIngredientRestock(ingredientId, restaurantId);
      setRestockMessage({ id: ingredientId, text: msg });
      setTimeout(() => setRestockMessage(null), 5000);
    } catch (err: any) {
      setRestockMessage({ id: ingredientId, text: `❌ ${err.message}` });
      setTimeout(() => setRestockMessage(null), 5000);
    } finally {
      setRestockingId(null);
    }
  };

  return (
    <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-4 pb-3 border-b border-stone-800">
        <div className="flex items-center gap-2">
          <Package className="w-5 h-5 text-amber-400" />
          <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-200">
            Kitchen Ingredient Stock
          </h3>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[9px] text-stone-600 font-mono">
            Updated {lastRefresh.toLocaleTimeString()}
          </span>
          <button
            onClick={() => setAlertSoundEnabled(!alertSoundEnabled)}
            className={`p-1.5 rounded-lg transition-colors cursor-pointer ${
              alertSoundEnabled
                ? 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                : 'bg-stone-800 text-stone-600 border border-stone-700'
            }`}
            title={alertSoundEnabled ? 'Mute depletion alerts' : 'Unmute depletion alerts'}
          >
            <Volume2 className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => { setIsLoading(true); loadIngredients(); }}
            className="p-1.5 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-400 hover:text-stone-200 transition-colors cursor-pointer"
            title="Refresh stock levels"
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Low-stock alerts banner */}
      {lowStockAlerts.length > 0 && (
        <div className="mb-4 bg-amber-500/10 border border-amber-500/30 rounded-xl p-3">
          <div className="flex items-center gap-2 mb-1">
            <Bell className="w-4 h-4 text-amber-400 animate-pulse" />
            <span className="text-[10px] font-bold text-amber-400 uppercase tracking-wider">
              Recent Low-Stock Alerts
            </span>
          </div>
          <div className="space-y-1">
            {lowStockAlerts.slice(0, 3).map((alert, i) => (
              <p key={i} className="text-[11px] text-amber-300/80">
                {alert.orderNumber && <span className="font-mono font-bold">{alert.orderNumber}: </span>}
                {alert.lowStockIngredients.map(d => `${d.name} (${d.stockQuantity} ${d.unit})`).join(', ')}
              </p>
            ))}
          </div>
        </div>
      )}

      {/* Summary cards */}
      <div className="grid grid-cols-3 gap-2 mb-4">
        <button
          onClick={() => setFilter(filter === 'depleted' ? 'all' : 'depleted')}
          className={`rounded-xl p-3 border text-center transition-all cursor-pointer ${getStockBgColor('depleted')}`}
        >
          <XCircle className="w-4 h-4 text-rose-400 mx-auto mb-1" />
          <span className="text-lg font-mono font-bold text-rose-400 block">{depletedCount}</span>
          <span className="text-[9px] font-bold text-rose-400/70 uppercase tracking-wider">Out of Stock</span>
        </button>
        <button
          onClick={() => setFilter(filter === 'low' ? 'all' : 'low')}
          className={`rounded-xl p-3 border text-center transition-all cursor-pointer ${getStockBgColor('low')}`}
        >
          <AlertTriangle className="w-4 h-4 text-amber-400 mx-auto mb-1" />
          <span className="text-lg font-mono font-bold text-amber-400 block">{lowCount}</span>
          <span className="text-[9px] font-bold text-amber-400/70 uppercase tracking-wider">Low Stock</span>
        </button>
        <button
          onClick={() => setFilter(filter === 'ok' ? 'all' : 'ok')}
          className={`rounded-xl p-3 border text-center transition-all cursor-pointer ${getStockBgColor('ok')}`}
        >
          <CheckCircle2 className="w-4 h-4 text-emerald-400 mx-auto mb-1" />
          <span className="text-lg font-mono font-bold text-emerald-400 block">{okCount}</span>
          <span className="text-[9px] font-bold text-emerald-400/70 uppercase tracking-wider">In Stock</span>
        </button>
      </div>

      {/* Ingredient stock list */}
      {isLoading ? (
        <div className="text-center py-6 text-stone-500 text-xs">Loading stock levels...</div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-6 text-stone-500 text-xs">
          {filter === 'all' ? 'No active ingredients.' : `No ${filter} ingredients.`}
        </div>
      ) : (
        <div className="space-y-2 max-h-[400px] overflow-y-auto pr-1">
          {filtered
            .sort((a, b) => {
              const order = { depleted: 0, low: 1, ok: 2 };
              return order[getStockLevel(a)] - order[getStockLevel(b)];
            })
            .map((ing) => {
              const level = getStockLevel(ing);
              const threshold = (ing.lowStockThreshold != null && ing.lowStockThreshold > 0)
                ? ing.lowStockThreshold : ing.reorderLevel;
              const pct = threshold > 0
                ? Math.min(100, Math.round((ing.stockQuantity / threshold) * 100))
                : 100;

              return (
                <div
                  key={ing.id}
                  className={`flex items-center gap-3 p-3 rounded-xl border transition-all ${getStockBgColor(level)}`}
                >
                  {/* Status dot */}
                  <div className={`w-2 h-2 rounded-full shrink-0 ${getStockBarColor(level)} shadow-sm`} />

                  {/* Name + bar */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs font-bold text-stone-200 truncate">
                        {ing.displayName || ing.name}
                      </span>
                      <span className={`text-[10px] font-bold uppercase tracking-wider ${getStockTextColor(level)}`}>
                        {getStockLabel(level)}
                      </span>
                    </div>
                    {/* Stock bar */}
                    <div className="w-full bg-stone-950 rounded-full h-1.5 overflow-hidden">
                      <div
                        className={`h-1.5 rounded-full transition-all duration-500 shadow-sm ${getStockBarColor(level)}`}
                        style={{ width: `${Math.min(100, pct)}%` }}
                      />
                    </div>
                  </div>

                  {/* Stock numbers + restock button */}
                  <div className="text-right shrink-0 flex flex-col items-end gap-1">
                    <span className={`text-sm font-mono font-bold ${getStockTextColor(level)}`}>
                      {ing.stockQuantity}
                    </span>
                    <span className="text-[10px] text-stone-500 ml-0.5">{ing.unit}</span>
                    {threshold > 0 && (
                      <div className="text-[9px] text-stone-600 font-mono">
                        warn: {threshold} {ing.unit}
                      </div>
                    )}
                    {(level === 'low' || level === 'depleted') && (
                      <div>
                        {restockMessage?.id === ing.id ? (
                          <span className="text-[9px] text-emerald-400 font-bold animate-pulse">
                            {restockMessage.text}
                          </span>
                        ) : (
                          <button
                            onClick={() => handleRestock(ing.id)}
                            disabled={restockingId === ing.id}
                            className="flex items-center gap-1 text-[9px] font-bold px-2 py-1 rounded-lg bg-blue-500/10 text-blue-400 border border-blue-500/30 hover:bg-blue-500/20 transition-colors cursor-pointer disabled:opacity-50"
                          >
                            <Truck className="w-3 h-3" />
                            {restockingId === ing.id ? 'Sending...' : 'Restock'}
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
};
