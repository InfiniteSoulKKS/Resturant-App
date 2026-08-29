import React, { useEffect, useState } from 'react';
import {
  ShoppingBasket,
  TrendingUp,
  AlertTriangle,
  CheckCircle2,
  Plus,
  X,
  Calculator,
  RefreshCw,
  CalendarX2,
  Search,
  Power,
  PowerOff,
  Package,
} from 'lucide-react';
import { Ingredient, IngredientForecast, DishForecast, IngredientForecastResponse, OperatingHour } from '../types';
import {
  listIngredients,
  createIngredient,
  updateIngredient,
  searchIngredients,
  deactivateIngredient,
  reactivateIngredient,
  getIngredientUsage,
  getIngredientForecast,
  getOperatingHours,
} from '../lib/apiClient';

interface IngredientPlanningProps {
  restaurantId: string;
  canManage?: boolean;
}

const CATEGORIES = ['Grains', 'Meat', 'Vegetables', 'Dairy', 'Spices', 'Oils', 'Beverages', 'Condiments', 'Other'];
const UNITS = ['g', 'kg', 'ml', 'litre', 'piece'];

export const IngredientPlanning: React.FC<IngredientPlanningProps> = ({ restaurantId, canManage = true }) => {
  const [ingredients, setIngredients] = useState<Ingredient[]>([]);
  const [forecast, setForecast] = useState<IngredientForecastResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isForecastLoading, setIsForecastLoading] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  // Master management state
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const [usageCounts, setUsageCounts] = useState<Record<string, number>>({});

  const [form, setForm] = useState({
    name: '',
    displayName: '',
    unit: 'g',
    category: '',
    stockQuantity: 0,
    reorderLevel: 0,
    lowStockThreshold: 0,
  });

  // Operating hours
  const [operatingHours, setOperatingHours] = useState<OperatingHour[]>([]);

  // 7-day date picker
  const toDateInput = (d: Date) => {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  };
  const DAY_NAMES = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const generateDateRange = (): { date: string; label: string; dayName: string; dayOfWeek: number }[] => {
    const days = [];
    for (let i = 1; i <= 7; i++) {
      const d = new Date(Date.now() + i * 24 * 60 * 60 * 1000);
      days.push({
        date: toDateInput(d),
        label: `${d.getDate()}/${d.getMonth() + 1}`,
        dayName: DAY_NAMES[d.getDay()],
        dayOfWeek: d.getDay() === 0 ? 7 : d.getDay(),
      });
    }
    return days;
  };
  const dateRange = generateDateRange();
  const [forecastDate, setForecastDate] = useState<string>(dateRange[0]?.date || '');

  const isClosedDay = (dayOfWeek: number): boolean => {
    const hour = operatingHours.find((h) => h.dayOfWeek === dayOfWeek);
    if (!hour) return false;
    if (hour.closed) return true;
    if (hour.closeTime) {
      const [h, m] = hour.closeTime.split(':').map(Number);
      if (h < 14 || (h === 14 && m === 0)) return true;
    }
    return false;
  };

  const dayHoursLabel = (dayOfWeek: number): string => {
    const hour = operatingHours.find((h) => h.dayOfWeek === dayOfWeek);
    if (!hour || hour.closed) return 'Closed';
    if (hour.openTime && hour.closeTime) return `${hour.openTime}–${hour.closeTime}`;
    return 'Open';
  };

  const load = async () => {
    try {
      const [ing, hours] = await Promise.all([
        listIngredients(restaurantId),
        getOperatingHours(restaurantId).catch(() => []),
      ]);
      setIngredients(ing);
      setOperatingHours(hours);
      // Load usage counts for active ingredients
      const counts: Record<string, number> = {};
      await Promise.all(
        ing.map(async (i) => {
          try {
            counts[i.id] = await getIngredientUsage(i.id);
          } catch {
            counts[i.id] = 0;
          }
        })
      );
      setUsageCounts(counts);
    } catch (err) {
      setMessage({ type: 'err', text: `Failed to load ingredients: ${err}` });
    } finally {
      setIsLoading(false);
    }
  };

  const loadForecast = async (date?: string) => {
    setIsForecastLoading(true);
    setForecast(null);
    try {
      const f = await getIngredientForecast(restaurantId, date);
      setForecast(f);
    } catch (err) {
      setMessage({ type: 'err', text: `Forecast failed: ${err}` });
    } finally {
      setIsForecastLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [restaurantId]);

  // Filtered ingredients
  const filteredIngredients = ingredients.filter((i) => {
    const matchesSearch = !searchQuery ||
      i.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (i.displayName && i.displayName.toLowerCase().includes(searchQuery.toLowerCase()));
    const matchesStatus = statusFilter === 'all' ||
      (statusFilter === 'active' && i.active !== false) ||
      (statusFilter === 'inactive' && i.active === false);
    return matchesSearch && matchesStatus;
  });

  const activeCount = ingredients.filter((i) => i.active !== false).length;
  const inactiveCount = ingredients.filter((i) => i.active === false).length;

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        name: form.displayName || form.name,
        displayName: form.displayName || form.name,
        unit: form.unit,
        category: form.category || undefined,
        stockQuantity: form.stockQuantity,
        reorderLevel: form.reorderLevel,
      };
      if (editingId) {
        await updateIngredient(editingId, payload, restaurantId);
        setMessage({ type: 'ok', text: '✅ Ingredient updated' });
      } else {
        await createIngredient(payload, restaurantId);
        setMessage({ type: 'ok', text: '✅ Ingredient added to master' });
      }
      setShowAddForm(false);
      setEditingId(null);
      setForm({ name: '', displayName: '', unit: 'g', category: '', stockQuantity: 0, reorderLevel: 0, lowStockThreshold: 0 });
      await load();
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const handleToggleActive = async (id: string, currentlyActive: boolean) => {
    try {
      if (currentlyActive) {
        await deactivateIngredient(id, restaurantId);
        setMessage({ type: 'ok', text: '✅ Ingredient deactivated' });
      } else {
        await reactivateIngredient(id, restaurantId);
        setMessage({ type: 'ok', text: '✅ Ingredient reactivated' });
      }
      await load();
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  return (
    <div className="max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-8">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
            <ShoppingBasket className="w-8 h-8 text-amber-400" />
            <span>Ingredient Master & Forecast</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Manage your restaurant's ingredient master list and compute pre-order requirements.
          </p>
        </div>
        {canManage && (
          <button
            onClick={() => { setShowAddForm(true); setEditingId(null); setForm({ name: '', displayName: '', unit: 'g', category: '', stockQuantity: 0, reorderLevel: 0, lowStockThreshold: 0 }); }}
            className="flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold px-4 py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
          >
            <Plus className="w-4 h-4 stroke-[3]" />
            Add Ingredient
          </button>
        )}
      </div>

      {message && (
        <div className={`mb-6 p-3 rounded-xl border text-xs flex items-center gap-2 ${
          message.type === 'ok'
            ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
            : 'bg-rose-500/10 border-rose-500/30 text-rose-400'
        }`}>
          {message.type === 'ok' ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertTriangle className="w-4 h-4 shrink-0" />}
          {message.text}
        </div>
      )}

      {/* 7-Day Pre-Order Date Selector */}
      <div className="mb-6 bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-stone-800 shadow-xl">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-300">
            Pre-Order Forecast — Select Date (Next 7 Days)
          </h3>
          <button
            onClick={() => loadForecast(forecastDate)}
            disabled={isForecastLoading}
            className="flex items-center gap-2 bg-violet-600 hover:bg-violet-500 text-white text-[11px] font-bold px-3 py-2 rounded-xl transition-all shadow-lg shadow-violet-600/20 cursor-pointer"
          >
            <Calculator className="w-3.5 h-3.5" />
            {isForecastLoading ? 'Computing...' : 'Compute Forecast'}
          </button>
        </div>
        <div className="grid grid-cols-7 gap-2">
          {dateRange.map((day) => {
            const closed = isClosedDay(day.dayOfWeek);
            const selected = forecastDate === day.date;
            return (
              <button
                key={day.date}
                onClick={() => { if (!closed) setForecastDate(day.date); }}
                disabled={closed}
                className={`relative flex flex-col items-center py-2.5 px-1 rounded-xl border text-center transition-all ${
                  closed
                    ? 'bg-stone-950/50 border-stone-800/50 cursor-not-allowed opacity-50'
                    : selected
                      ? 'bg-violet-600/20 border-violet-500/60 text-violet-300 shadow-lg shadow-violet-500/10'
                      : 'bg-stone-950 border-stone-800 hover:border-stone-700 hover:bg-stone-900 cursor-pointer'
                }`}
              >
                <span className={`text-[9px] font-mono uppercase ${closed ? 'text-stone-600' : selected ? 'text-violet-400' : 'text-stone-500'}`}>
                  {day.dayName}
                </span>
                <span className={`text-sm font-bold mt-0.5 ${closed ? 'text-stone-600 line-through' : selected ? 'text-violet-300' : 'text-stone-200'}`}>
                  {day.label}
                </span>
                {closed ? (
                  <span className="mt-1 flex items-center gap-0.5 text-[8px] text-rose-500 font-bold">
                    <CalendarX2 className="w-2.5 h-2.5" />
                    CLOSED
                  </span>
                ) : (
                  <span className="mt-1 text-[8px] text-stone-600 font-mono">{dayHoursLabel(day.dayOfWeek)}</span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Forecast Results */}
      {forecast && (
        <div className="mb-8 bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-violet-800/50 shadow-xl">
          <div className="flex items-center justify-between gap-2 mb-4 pb-3 border-b border-stone-800">
            <div className="flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-violet-400" />
              <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-200">
                Pre-Order Ingredient Forecast — {forecastDate}
              </h3>
            </div>
          </div>

          {forecast.dishes.length > 0 && (
            <div className="mb-6">
              <h4 className="text-[11px] font-bold uppercase tracking-wider text-stone-400 mb-2">
                Per-Dish Requirement
              </h4>
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
                {forecast.dishes.map((d: DishForecast) => (
                  <div key={d.menuItemId} className="bg-stone-950 rounded-xl border border-stone-800 p-3.5">
                    <div className="flex justify-between items-center mb-2">
                      <span className="text-xs font-bold text-stone-100">{d.dish}</span>
                      <span className="text-[10px] font-mono text-amber-400 bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-lg">
                        {d.plates} plate{d.plates !== 1 ? 's' : ''}
                      </span>
                    </div>
                    <div className="space-y-1">
                      {d.ingredients.map((ing, j) => (
                        <div key={j} className="flex justify-between text-[11px]">
                          <span className="text-stone-400">{ing.name}</span>
                          <span className="font-mono text-stone-200">{ing.requiredQuantity} {ing.unit}</span>
                        </div>
                      ))}
                      {d.ingredients.length === 0 && (
                        <p className="text-[11px] text-stone-600">No recipe configured for this dish.</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-stone-500 font-mono uppercase text-[10px] border-b border-stone-800">
                  <th className="text-left py-2 pr-3">Ingredient</th>
                  <th className="text-right py-2 px-2">Required</th>
                  <th className="text-right py-2 px-2">Stock</th>
                  <th className="text-right py-2 px-2">Shortfall</th>
                  <th className="text-center py-2 pl-2">Need Purchase</th>
                </tr>
              </thead>
              <tbody>
                {forecast.ingredients.map((f, i) => (
                  <tr key={i} className={`border-b border-stone-800/50 ${f.needPurchase ? 'bg-rose-500/5' : ''}`}>
                    <td className="py-2.5 pr-3 font-medium text-stone-200">{f.name}</td>
                    <td className="py-2.5 px-2 text-right font-mono text-amber-400">{f.requiredQuantity} {f.unit}</td>
                    <td className="py-2.5 px-2 text-right font-mono text-stone-400">{f.currentStock} {f.unit}</td>
                    <td className={`py-2.5 px-2 text-right font-mono font-bold ${f.shortfall > 0 ? 'text-rose-400' : 'text-emerald-400'}`}>
                      {f.shortfall} {f.unit}
                    </td>
                    <td className="py-2.5 pl-2 text-center">
                      {f.needPurchase ? (
                        <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/30">
                          <AlertTriangle className="w-3 h-3" /> ORDER
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                          <CheckCircle2 className="w-3 h-3" /> OK
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {forecast.ingredients.length === 0 && (
            <p className="text-center py-8 text-stone-500 text-xs">No pre-orders found for {forecastDate}. Forecast is empty.</p>
          )}
        </div>
      )}

      {/* Ingredient Master Management */}
      <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
        <div className="flex items-center gap-2 mb-4 pb-3 border-b border-stone-800">
          <Package className="w-5 h-5 text-amber-400" />
          <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-200">
            Ingredient Master ({activeCount} active{inactiveCount > 0 ? `, ${inactiveCount} inactive` : ''})
          </h3>
        </div>

        {/* Search + Filter */}
        <div className="flex flex-col sm:flex-row gap-2 mb-4">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-stone-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search ingredients..."
              className="w-full pl-9 pr-3 py-2 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-200 focus:outline-none focus:border-amber-500"
            />
          </div>
          <div className="flex gap-1">
            {(['all', 'active', 'inactive'] as const).map((f) => (
              <button
                key={f}
                onClick={() => setStatusFilter(f)}
                className={`px-3 py-2 text-[10px] font-bold uppercase rounded-xl border transition-all cursor-pointer ${
                  statusFilter === f
                    ? 'bg-amber-500/15 border-amber-500/50 text-amber-400'
                    : 'bg-stone-950 border-stone-800 text-stone-500 hover:text-stone-300'
                }`}
              >
                {f}
              </button>
            ))}
          </div>
        </div>

        {/* Ingredients Table */}
        {isLoading ? (
          <div className="text-center py-8 text-stone-500 text-xs">Loading...</div>
        ) : filteredIngredients.length === 0 ? (
          <div className="text-center py-8 text-stone-500 text-xs">
            {searchQuery ? 'No ingredients match your search.' : 'No ingredients added yet.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-stone-500 font-mono uppercase text-[10px] border-b border-stone-800">
                  <th className="text-left py-2 pr-3">Ingredient</th>
                  <th className="text-left py-2 px-2">Unit</th>
                  <th className="text-left py-2 px-2">Category</th>
                  <th className="text-right py-2 px-2">Stock</th>
                  <th className="text-right py-2 px-2">Reorder</th>
                  <th className="text-right py-2 px-2">Kitchen Warn</th>
                  <th className="text-center py-2 px-2">Used In</th>
                  <th className="text-center py-2 px-2">Status</th>
                  {canManage && <th className="text-center py-2 pl-2">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {filteredIngredients.map((ing) => {                   const threshold = (ing.lowStockThreshold != null && ing.lowStockThreshold > 0) ? ing.lowStockThreshold : ing.reorderLevel;
                   const low = threshold > 0 && ing.stockQuantity <= threshold;
                  const inactive = ing.active === false;
                  const usageCount = usageCounts[ing.id] || 0;
                  return (
                    <tr key={ing.id} className={`border-b border-stone-800/50 ${inactive ? 'opacity-50' : ''} ${low && !inactive ? 'bg-rose-500/5' : ''}`}>
                      <td className="py-2.5 pr-3">
                        <span className={`font-medium ${inactive ? 'text-stone-500 line-through' : 'text-stone-200'}`}>
                          {ing.displayName || ing.name}
                        </span>
                      </td>
                      <td className="py-2.5 px-2 font-mono text-stone-400">{ing.unit}</td>
                      <td className="py-2.5 px-2 text-stone-500">{ing.category || '—'}</td>
                      <td className={`py-2.5 px-2 text-right font-mono ${low && !inactive ? 'text-rose-400 font-bold' : 'text-stone-300'}`}>
                        {ing.stockQuantity} {ing.unit}
                      </td>
                      <td className="py-2.5 px-2 text-right font-mono text-stone-500">{ing.reorderLevel} {ing.unit}</td>
                      <td className="py-2.5 px-2 text-right font-mono text-stone-500">{ing.lowStockThreshold ?? ing.reorderLevel} {ing.unit}</td>
                      <td className="py-2.5 px-2 text-center">
                        <span className="text-stone-400 font-mono">{usageCount}</span>
                        <span className="text-stone-600"> dish{usageCount !== 1 ? 'es' : ''}</span>
                      </td>
                      <td className="py-2.5 px-2 text-center">
                        {inactive ? (
                          <span className="text-[10px] px-2 py-0.5 rounded-lg bg-stone-800 text-stone-500 border border-stone-700">
                            Inactive
                          </span>
                        ) : low ? (
                          <span className="text-[10px] px-2 py-0.5 rounded-lg bg-rose-500/10 text-rose-400 border border-rose-500/30">
                            Low Stock
                          </span>
                        ) : (
                          <span className="text-[10px] px-2 py-0.5 rounded-lg bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                            Active
                          </span>
                        )}
                      </td>
                      {canManage && (
                        <td className="py-2.5 pl-2 text-center">
                          <div className="flex items-center justify-center gap-1">
                            <button
                              onClick={() => {
                                setEditingId(ing.id);
                                setForm({
                                  name: ing.name,
                                  displayName: ing.displayName || ing.name,
                                  unit: ing.unit,
                                  category: ing.category || '',
                                  stockQuantity: ing.stockQuantity,
                                  reorderLevel: ing.reorderLevel,
                                  lowStockThreshold: ing.lowStockThreshold ?? 0,
                                });
                                setShowAddForm(true);
                              }}
                              className="text-stone-500 hover:text-amber-400 text-[10px] font-bold px-2 py-1 rounded-lg hover:bg-stone-800 transition-colors cursor-pointer"
                            >
                              Edit
                            </button>
                            <button
                              onClick={() => handleToggleActive(ing.id, ing.active !== false)}
                              className={`p-1 rounded-lg transition-colors cursor-pointer ${
                                inactive
                                  ? 'text-stone-600 hover:text-emerald-400 hover:bg-stone-800'
                                  : 'text-stone-600 hover:text-rose-400 hover:bg-stone-800'
                              }`}
                              title={inactive ? 'Reactivate' : 'Deactivate'}
                            >
                              {inactive ? <Power className="w-3.5 h-3.5" /> : <PowerOff className="w-3.5 h-3.5" />}
                            </button>
                          </div>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add/Edit Ingredient Modal */}
      {showAddForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
          <div className="bg-stone-900 border border-stone-700 rounded-3xl max-w-sm w-full p-6 relative my-8 text-stone-100">
            <div className="flex justify-between items-center pb-4 border-b border-stone-800">
              <h3 className="text-base font-bold font-serif flex items-center gap-2">
                <ShoppingBasket className="w-5 h-5 text-amber-400" />
                {editingId ? 'Edit Ingredient' : 'Add Ingredient'}
              </h3>
              <button onClick={() => setShowAddForm(false)} className="text-stone-400 hover:text-stone-100 p-1">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleSave} className="mt-4 space-y-3 text-xs">
              <div>
                <label className="text-stone-500 text-[10px] mb-1 block">Display Name *</label>
                <input
                  required placeholder="e.g. Chicken Breast"
                  value={form.displayName}
                  onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                  className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-stone-500 text-[10px] mb-1 block">Base Unit *</label>
                  <select
                    value={form.unit}
                    onChange={(e) => setForm({ ...form, unit: e.target.value })}
                    className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100"
                  >
                    {UNITS.map((u) => <option key={u} value={u}>{u}</option>)}
                  </select>
                </div>
                <div>
                  <label className="text-stone-500 text-[10px] mb-1 block">Category</label>
                  <select
                    value={form.category}
                    onChange={(e) => setForm({ ...form, category: e.target.value })}
                    className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100"
                  >
                    <option value="">—</option>
                    {CATEGORIES.map((c) => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-stone-500 text-[10px] mb-1 block">Stock Quantity</label>
                  <input
                    required type="number" step="0.001" value={form.stockQuantity}
                    onChange={(e) => setForm({ ...form, stockQuantity: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 font-mono"
                  />
                </div>
                <div>
                  <label className="text-stone-500 text-[10px] mb-1 block">Reorder Level (restock alert)</label>
                  <input
                    required type="number" step="0.001" value={form.reorderLevel}
                    onChange={(e) => setForm({ ...form, reorderLevel: parseFloat(e.target.value) || 0 })}
                    className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 font-mono"
                  />
                </div>
              </div>
              <div>
                <label className="text-stone-500 text-[10px] mb-1 block">Kitchen Low-Stock Warning (at or below this, kitchen sees amber alert)</label>
                <input
                  type="number" step="0.001" value={form.lowStockThreshold}
                  onChange={(e) => setForm({ ...form, lowStockThreshold: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 font-mono"
                  placeholder="0 = use reorder level"
                />
                <p className="text-[9px] text-stone-600 mt-1">Set to 0 to fall back to reorder level.</p>
              </div>
              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setShowAddForm(false)}
                  className="flex-1 py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-300 font-bold rounded-xl cursor-pointer">
                  Cancel
                </button>
                <button type="submit"
                  className="flex-1 py-2.5 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold rounded-xl cursor-pointer">
                  {editingId ? 'Update' : 'Add'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
