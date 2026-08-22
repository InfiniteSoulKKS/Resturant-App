import React, { useState, useEffect, useMemo } from 'react';
import { MenuItem, Category, CartItem, PreOrderDateOption } from '../types';
import { getPreOrderDates, getPlateAvailability, type PlateAvailabilityItem } from '../lib/apiClient';
import {
  Plus,
  Minus,
  ShoppingBag,
  Flame,
  Sparkles,
  Search,
  ArrowRight,
  SearchX,
  ChefHat,
  Clock,
  Award,
  LogIn,
  CalendarClock,
  CheckCircle2,
  XCircle,
  Loader2,
  AlertTriangle,
} from 'lucide-react';

interface CustomerMenuViewProps {
  menuItems: MenuItem[];
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  cart: CartItem[];
  addToCart: (item: MenuItem) => void;
  removeFromCart: (itemId: string) => void;
  onProceedToPayment: () => void;
  /** False for staff accounts — hides the ordering flow (staff manage the kitchen). */
  allowOrdering?: boolean;
  /** Opens the auth modal so a signed-in staff member can switch to a customer account. */
  onOpenAuth?: () => void;
  /** Current restaurant scope — used to load the pre-order availability calendar. */
  restaurantId?: string;
  /** Called when the customer picks a date in the calendar ('' when none). */
  onPreOrderDateChange?: (date: string) => void;
  /** Real-time plate count updates from SSE — menuItemId → remaining plates. */
  plateUpdates?: Map<string, number>;
}

const CATEGORIES: Category[] = ['All Items', 'Starters', 'Mains', 'Breads', 'Desserts', 'Beverages'];

export const CustomerMenuView: React.FC<CustomerMenuViewProps> = ({
  menuItems,
  searchQuery,
  setSearchQuery,
  cart,
  addToCart,
  removeFromCart,
  onProceedToPayment,
  allowOrdering = true,
  onOpenAuth,
  restaurantId,
  onPreOrderDateChange,
  plateUpdates,
}) => {
  const [selectedCategory, setSelectedCategory] = useState<Category>('All Items');
  const [dietFilter, setDietFilter] = useState<'ALL' | 'VEG' | 'NON_VEG'>('ALL');

  // Pre-order availability calendar — surfaced on the menu itself so customers
  // can see which upcoming days dishes can be pre-ordered before checkout.
  const [preOrderDates, setPreOrderDates] = useState<PreOrderDateOption[]>([]);
  const [selectedDate, setSelectedDate] = useState<string>('');
  const [dishFilter, setDishFilter] = useState<string>(''); // '' = all dishes
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarError, setCalendarError] = useState<string | null>(null);

  // Plate availability — tracks remaining plates per dish for low-stock indicators
  const [plateAvailability, setPlateAvailability] = useState<PlateAvailabilityItem[]>([]);

  useEffect(() => {
    if (!restaurantId || menuItems.length === 0) return;
    let cancelled = false;
    setCalendarLoading(true);
    setCalendarError(null);
    getPreOrderDates({
      restaurantId,
      menuItemIds: menuItems.map((m) => m.id),
    })
      .then((dates) => {
        if (cancelled) return;
        setPreOrderDates(dates);
        setSelectedDate((prev) => prev && dates.some((d) => d.date === prev) ? prev : (dates[0]?.date || ''));
      })
      .catch((err) => {
        if (!cancelled) setCalendarError(err?.message || 'Could not load pre-order availability');
      })
      .finally(() => {
        if (!cancelled) setCalendarLoading(false);
      });
    return () => { cancelled = true; };
  }, [restaurantId, menuItems]);

  // Filter the calendar to dates where the chosen dish is pre-orderable.
  const filteredDates = useMemo(() => {
    if (!dishFilter) return preOrderDates;
    return preOrderDates.filter((d) =>
      d.dishes.some((x) => x.menuItemId === dishFilter && x.available)
    );
  }, [preOrderDates, dishFilter]);

  // Keep the selection valid under the dish filter, and surface the chosen
  // date to the parent so checkout can preselect it.
  useEffect(() => {
    if (filteredDates.length > 0 && !filteredDates.some((d) => d.date === selectedDate)) {
      setSelectedDate(filteredDates[0].date);
    } else if (filteredDates.length === 0 && selectedDate) {
      setSelectedDate('');
    }
  }, [filteredDates, selectedDate]);

  useEffect(() => {
    onPreOrderDateChange?.(selectedDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedDate]);

  // Fetch plate availability for low-stock indicators + periodic refresh every 30s
  useEffect(() => {
    if (!restaurantId || menuItems.length === 0) return;
    let cancelled = false;
    const fetchPlates = () => {
      getPlateAvailability(restaurantId)
        .then((items) => { if (!cancelled) setPlateAvailability(items); })
        .catch(() => { /* ignore — low stock is informational */ });
    };
    fetchPlates(); // initial fetch
    const interval = setInterval(fetchPlates, 30_000); // refresh every 30s
    return () => { cancelled = true; clearInterval(interval); };
  }, [restaurantId, menuItems.length]);

  /** Lookup plate remaining for a menu item. Returns null if no cap. */
  const getPlateRemaining = (menuItemId: string): number | null => {
    // Check real-time SSE updates first (most current)
    if (plateUpdates?.has(menuItemId)) {
      return plateUpdates.get(menuItemId)!;
    }
    // Fall back to initial fetch data
    const pa = plateAvailability.find((p) => p.menuItemId === menuItemId);
    if (!pa || pa.dailyPlateCount === null) return null; // unlimited
    return pa.remaining;
  };

  const filteredItems = menuItems.filter((item) => {
    const matchesSearch =
      item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.description.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesCategory =
      selectedCategory === 'All Items' ||
      (selectedCategory === 'Starters' && (item.category === 'Starters' || item.category === 'Appetizers')) ||
      item.category === selectedCategory;

    const matchesDiet =
      dietFilter === 'ALL' ||
      (dietFilter === 'VEG' && item.isVeg === true) ||
      (dietFilter === 'NON_VEG' && item.isVeg === false);

    return matchesSearch && matchesCategory && matchesDiet;
  });

  const totalItemCount = cart.reduce((sum, ci) => sum + ci.quantity, 0);
  const totalCartPrice = cart.reduce((sum, ci) => sum + ci.menuItem.price * ci.quantity, 0);

  // Detect cart items that are now sold out (changed since customer added them)
  const unavailableInCart = cart.filter((ci) => {
    const current = menuItems.find((m) => m.id === ci.menuItem.id);
    return current && current.status === 'Sold Out';
  });
  const hasUnavailable = unavailableInCart.length > 0;

  /** "Wed, 20 Aug" from a yyyy-MM-dd string (business-day labels, IST-safe). */
  const prettyDate = (dateStr: string): string => {
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
  };

  /** Day-of-month + month for the compact calendar chip. */
  const chipDate = (dateStr: string): { day: string; month: string } => {
    const d = new Date(dateStr + 'T00:00:00');
    return {
      day: String(d.getDate()),
      month: d.toLocaleDateString('en-IN', { month: 'short' }),
    };
  };

  /** Aggregate per-date status from the backend's per-dish availability. */
  const summarize = (d: PreOrderDateOption) => {
    const total = d.dishes.length;
    const available = d.dishes.filter((x) => x.available).length;
    const closed = d.reasons.some((r) => r.toLowerCase().includes('closed'));
    const cutoff = d.reasons.some((r) => r.toLowerCase().includes('cutoff'));
    let label = '';
    let tone: 'open' | 'partial' | 'closed' = 'open';
    if (closed || cutoff) {
      label = closed ? 'Closed' : 'Cutoff passed';
      tone = 'closed';
    } else if (available === 0) {
      label = 'Unavailable';
      tone = 'closed';
    } else if (total > 0 && available < total) {
      label = `${available}/${total} dishes`;
      tone = 'partial';
    } else {
      label = 'Available';
      tone = 'open';
    }
    return { total, available, closed, cutoff, label, tone };
  };

  const activeDate = filteredDates.find((d) => d.date === selectedDate) || filteredDates[0];

  /** Pre-order status of a dish on the currently selected calendar date. */
  const dishStatusOnActiveDate = (itemId: string): 'available' | 'unavailable' | 'none' => {
    if (!activeDate) return 'none';
    const dish = activeDate.dishes.find((x) => x.menuItemId === itemId);
    if (!dish) return 'none';
    return dish.available ? 'available' : 'unavailable';
  };

  return (
    <div className="pt-20 px-4 md:px-8 max-w-[1440px] mx-auto pb-36 md:pb-28">
      {/* Hero Welcome Banner */}
      <div className="relative rounded-3xl overflow-hidden bg-gradient-to-r from-stone-900 via-stone-900/90 to-amber-950/40 border border-stone-800 p-6 md:p-8 mb-8 shadow-2xl">
        <div className="absolute -right-10 -bottom-10 w-80 h-80 bg-amber-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 text-xs font-semibold mb-3">
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            <span>Artisanal Culinary Pre-Booking & Pickup</span>
          </div>
          <h2 className="text-2xl md:text-4xl font-bold font-serif text-stone-100 tracking-tight leading-tight">
            Crafted Delicacies, Scheduled for Perfection
          </h2>
          <p className="text-xs md:text-sm text-stone-400 mt-2 leading-relaxed">
            Explore our chef-crafted menu. Pre-order ahead with live kitchen prep tracking and instant multi-channel order updates.
          </p>

          <div className="flex flex-wrap items-center gap-4 mt-5 text-xs text-stone-300">
            <div className="flex items-center gap-1.5">
              <Award className="w-4 h-4 text-amber-400" />
              <span>Michelin-Inspired Chefs</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Clock className="w-4 h-4 text-amber-400" />
              <span>Guaranteed Slot Pickups</span>
            </div>
            <div className="flex items-center gap-1.5">
              <ChefHat className="w-4 h-4 text-amber-400" />
              <span>100% Fresh Daily Prep</span>
            </div>
          </div>
        </div>
      </div>

      {/* Staff notice — ordering is customer-only. Prompt to switch accounts
          rather than silently hiding the flow. */}
      {!allowOrdering && (
        <div className="mb-6 rounded-2xl border border-amber-500/30 bg-gradient-to-r from-amber-950/50 via-stone-900/90 to-stone-900/90 p-4 md:p-5 flex flex-col sm:flex-row sm:items-center gap-4 shadow-xl">
          <div className="flex items-center gap-3 flex-1">
            <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center shrink-0">
              <ChefHat className="w-5 h-5 text-amber-400" />
            </div>
            <div>
              <p className="text-sm font-bold text-amber-300">Sign in as a customer to order</p>
              <p className="text-xs text-stone-400 mt-0.5 leading-relaxed">
                You're signed in as staff — placing orders is for customer accounts. Manage kitchen orders from the Orders tab instead.
              </p>
            </div>
          </div>
          {onOpenAuth && (
            <button
              onClick={onOpenAuth}
              className="flex-shrink-0 px-4 py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold tracking-wide transition-all shadow-lg shadow-amber-500/20 cursor-pointer flex items-center justify-center gap-2 active:scale-[0.98]"
            >
              <LogIn className="w-4 h-4" />
              Sign in as customer
            </button>
          )}
        </div>
      )}

      {/* Pre-Order Availability Calendar — visible while browsing, not just at checkout */}
      {restaurantId && (calendarLoading || preOrderDates.length > 0 || calendarError) && (
        <div className="mb-8 rounded-3xl border border-stone-800 bg-stone-900/60 p-4 md:p-5 shadow-xl">
          <div className="flex items-center justify-between mb-4 flex-wrap gap-3">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center shrink-0">
                <CalendarClock className="w-4.5 h-4.5 text-amber-400" />
              </div>
              <div>
                <h3 className="text-sm font-bold font-serif text-stone-100 tracking-tight">
                  Pre-Order Availability
                </h3>
                <p className="text-[11px] text-stone-500">
                  Upcoming days when these dishes can be pre-ordered — pick a date to see per-dish status.
                </p>
              </div>
            </div>
            <div className="flex items-center gap-3 flex-wrap">
              {/* Dish filter — narrows the calendar to a single dish */}
              <select
                value={dishFilter}
                onChange={(e) => setDishFilter(e.target.value)}
                className="py-1.5 px-2.5 bg-stone-950 border border-stone-800 rounded-lg text-[11px] text-stone-200 focus:outline-none focus:border-amber-500 cursor-pointer"
                title="Filter calendar by dish"
              >
                <option value="">All dishes</option>
                {menuItems.map((m) => (
                  <option key={m.id} value={m.id}>{m.title}</option>
                ))}
              </select>
              <div className="flex items-center gap-3 text-[10px] text-stone-400">
                <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-emerald-400"></span>Open</span>
                <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-amber-400"></span>Partial</span>
                <span className="flex items-center gap-1.5"><span className="w-2 h-2 rounded-full bg-rose-400"></span>Closed / Cutoff</span>
              </div>
            </div>
          </div>

          {calendarLoading && preOrderDates.length === 0 ? (
            <div className="flex items-center justify-center py-8 text-xs text-stone-500 gap-2">
              <Loader2 className="w-4 h-4 animate-spin text-amber-400" />
              Checking upcoming pre-order slots...
            </div>
          ) : calendarError && preOrderDates.length === 0 ? (
            <p className="text-[11px] text-stone-500 py-2">
              Availability calendar is temporarily unavailable — you can still pre-order at checkout.
            </p>
          ) : filteredDates.length > 0 ? (
            <>
              {/* Date chips — horizontal scroll on mobile */}
              <div className="flex gap-2 overflow-x-auto hide-scrollbar pb-1 -mx-1 px-1">
                {filteredDates.map((d) => {
                  const { label, tone } = summarize(d);
                  const { day, month } = chipDate(d.date);
                  const isActive = d.date === activeDate?.date;
                  const toneStyles = {
                    open: 'border-emerald-500/50 bg-emerald-500/10 text-emerald-300',
                    partial: 'border-amber-500/50 bg-amber-500/10 text-amber-300',
                    closed: 'border-rose-500/40 bg-rose-500/10 text-rose-300',
                  }[tone];
                  return (
                    <button
                      key={d.date}
                      onClick={() => setSelectedDate(d.date)}
                      className={`flex-shrink-0 w-[74px] rounded-2xl border p-2.5 text-center transition-all cursor-pointer ${
                        isActive
                          ? 'border-amber-500 bg-stone-950 shadow-lg shadow-amber-500/10'
                          : 'border-stone-800 bg-stone-950/60 hover:border-stone-600'
                      }`}
                    >
                      <div className="text-[10px] font-semibold text-stone-400 uppercase tracking-wide">
                        {d.weekday.slice(0, 3)}
                      </div>
                      <div className={`text-lg font-bold font-mono leading-tight ${isActive ? 'text-amber-400' : 'text-stone-100'}`}>
                        {day}
                      </div>
                      <div className="text-[10px] text-stone-500 mb-1.5">{month}</div>
                      <div className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded-md border text-[9px] font-semibold ${toneStyles}`}>
                        <span className={`w-1 h-1 rounded-full ${tone === 'open' ? 'bg-emerald-400' : tone === 'partial' ? 'bg-amber-400' : 'bg-rose-400'}`}></span>
                        {label}
                      </div>
                    </button>
                  );
                })}
              </div>

              {/* Detail panel for the selected date */}
              {activeDate && (
                <div className="mt-3 bg-stone-950/70 border border-stone-800 rounded-2xl p-3.5">
                  <div className="flex items-center justify-between flex-wrap gap-2 mb-2.5">
                    <div className="flex items-center gap-2">
                      <CalendarClock className="w-4 h-4 text-amber-400" />
                      <span className="text-xs font-bold text-stone-100">{prettyDate(activeDate.date)}</span>
                    </div>
                    {activeDate.openTime && (
                      <span className="text-[10px] text-stone-500">
                        Pickup: {activeDate.openTime} – {activeDate.closeTime}
                      </span>
                    )}
                  </div>
                  {activeDate.reasons.length > 0 && (
                    <p className="text-[11px] text-rose-400 bg-rose-500/10 border border-rose-500/20 rounded-xl px-2.5 py-1.5 mb-2">
                      {activeDate.reasons.join(' · ')}
                    </p>
                  )}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5 max-h-44 overflow-y-auto pr-1">
                    {activeDate.dishes.map((dish) => (
                      <div
                        key={dish.menuItemId}
                        className={`flex items-center gap-2 text-[11px] px-2 py-1.5 rounded-lg border ${
                          dish.available
                            ? 'text-emerald-300 border-emerald-800/50 bg-emerald-950/30'
                            : 'text-stone-500 border-stone-800 bg-stone-900/40'
                        } ${dishFilter === dish.menuItemId ? 'ring-1 ring-amber-500/50' : ''}`}
                      >
                        {dish.available ? (
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        ) : (
                          <XCircle className="w-3.5 h-3.5 text-stone-600 shrink-0" />
                        )}
                        <span className="truncate flex-1">{dish.title}</span>
                        <span className="shrink-0 font-semibold">
                          {dish.available ? 'Available' : dish.reason || 'Unavailable'}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="flex items-center gap-2 py-6 justify-center text-xs text-stone-500">
              <XCircle className="w-4 h-4 text-stone-600" />
              {dishFilter
                ? `“${menuItems.find((m) => m.id === dishFilter)?.title || 'This dish'}” has no open pre-order slots in the next few days.`
                : 'No pre-order slots are currently open.'}
            </div>
          )}
        </div>
      )}

      {/* Categories & Filter Bar */}
      <div className="mb-8 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-stone-900/60 p-2 md:p-3 rounded-2xl border border-stone-800/80">
          {/* Categories Pills */}
          <div className="flex space-x-2 overflow-x-auto hide-scrollbar py-1">
            {CATEGORIES.map((cat) => {
              const isActive = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`flex-shrink-0 px-4 py-2 rounded-xl text-xs font-medium tracking-wide whitespace-nowrap transition-all cursor-pointer ${
                    isActive
                      ? 'bg-amber-500 text-stone-950 font-bold shadow-md shadow-amber-500/20'
                      : 'bg-stone-900 text-stone-400 hover:text-stone-100 hover:bg-stone-800/80 border border-stone-800'
                  }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>

          {/* Veg / Non-Veg Toggle Bar */}
          <div className="flex items-center bg-stone-950 p-1 rounded-xl border border-stone-800 text-xs font-medium self-start sm:self-auto">
            <button
              onClick={() => setDietFilter('ALL')}
              className={`px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                dietFilter === 'ALL' ? 'bg-stone-800 text-stone-100 font-bold' : 'text-stone-400 hover:text-stone-200'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setDietFilter('VEG')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                dietFilter === 'VEG' ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-800/80 font-bold' : 'text-stone-400 hover:text-emerald-400'
              }`}
            >
              <span className="w-2.5 h-2.5 rounded-sm border border-emerald-500 flex items-center justify-center p-0.5">
                <span className="w-1 h-1 rounded-full bg-emerald-500"></span>
              </span>
              Pure Veg
            </button>
            <button
              onClick={() => setDietFilter('NON_VEG')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                dietFilter === 'NON_VEG' ? 'bg-rose-950/80 text-rose-400 border border-rose-800/80 font-bold' : 'text-stone-400 hover:text-rose-400'
              }`}
            >
              <span className="w-2.5 h-2.5 rounded-sm border border-rose-500 flex items-center justify-center p-0.5">
                <span className="w-1 h-1 rounded-full bg-rose-500"></span>
              </span>
              Non-Veg
            </button>
          </div>
        </div>
      </div>

      {/* Menu Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {filteredItems.map((item) => {
          const cartEntry = cart.find((c) => c.menuItem.id === item.id);
          const quantityInCart = cartEntry ? cartEntry.quantity : 0;
          const isSoldOut = item.status === 'Sold Out';

          return (
            <div
              key={item.id}
              className={`bg-stone-900/80 backdrop-blur-md rounded-2xl border border-stone-800 hover:border-stone-700 transition-all duration-300 overflow-hidden flex flex-col h-full shadow-xl hover:shadow-2xl hover:-translate-y-1 group ${
                isSoldOut ? 'opacity-60' : ''
              }`}
            >
              {/* Image Container */}
              <div className="relative w-full h-48 bg-stone-950 overflow-hidden">
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className={`w-full h-full object-cover transition-transform duration-500 group-hover:scale-105 ${
                    isSoldOut ? 'grayscale' : ''
                  }`}
                  referrerPolicy="no-referrer"
                />

                {/* Gradient overlay */}
                <div className="absolute inset-0 bg-gradient-to-t from-stone-950/80 via-transparent to-transparent"></div>

                {/* Dietary Icon Badge */}
                <div className="absolute top-3 left-3 bg-stone-950/90 backdrop-blur-md px-2 py-1 rounded-lg border border-stone-700/60 shadow-md flex items-center gap-1.5">
                  <div className={`w-3.5 h-3.5 border flex items-center justify-center p-0.5 ${item.isVeg !== false ? 'border-emerald-500' : 'border-rose-500'}`}>
                    <div className={`w-1.5 h-1.5 rounded-full ${item.isVeg !== false ? 'bg-emerald-500' : 'bg-rose-500'}`}></div>
                  </div>
                  <span className="text-[10px] font-bold text-stone-300 uppercase tracking-wider">
                    {item.isVeg !== false ? 'VEG' : 'NON-VEG'}
                  </span>
                </div>

                {/* Category Tag */}
                <div className="absolute top-3 right-3 bg-stone-950/80 backdrop-blur-md px-2.5 py-1 rounded-lg border border-stone-700/50 shadow-md">
                  <span className="text-[10px] text-amber-400 font-bold uppercase tracking-wider">
                    {item.tag || item.category}
                  </span>
                </div>

                {isSoldOut && (
                  <div className="absolute bottom-3 left-3 bg-rose-950/90 text-rose-400 border border-rose-800/80 text-[10px] uppercase font-bold px-2 py-1 rounded-lg shadow">
                    Sold Out
                  </div>
                )}
                {!isSoldOut && (() => {
                  const remaining = getPlateRemaining(item.id);
                  if (remaining === null) return null; // unlimited
                  const cap = item.dailyPlateCount || 30;
                  const pct = Math.round((remaining / cap) * 100);
                  const isLow = pct <= 20 && remaining > 0;
                  const isCritical = pct <= 10 && remaining > 0;
                  if (!isLow) return null;
                  return (
                    <div className={`absolute bottom-3 left-3 ${
                      isCritical
                        ? 'bg-amber-500/90 text-stone-950 border border-amber-400'
                        : 'bg-amber-950/90 text-amber-400 border border-amber-800/80'
                    } text-[10px] font-bold px-2 py-1 rounded-lg shadow flex items-center gap-1`}
                    >
                      <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z" />
                      </svg>
                      {remaining} left today
                    </div>
                  );
                })()}
              </div>

              {/* Card Details Body */}
              <div className="p-5 flex flex-col flex-grow">
                <div className="flex justify-between items-start mb-2">
                  <h3 className="text-base font-bold font-serif text-stone-100 group-hover:text-amber-400 transition-colors line-clamp-1">
                    {item.title}
                  </h3>
                  <span className="text-base font-bold font-mono text-amber-400 ml-2 whitespace-nowrap">
                    ₹{item.price}
                  </span>
                </div>

                <p className="text-xs text-stone-400 line-clamp-2 mb-4 flex-grow leading-relaxed">
                  {item.description}
                </p>

                {item.spiceLevel && (
                  <div className="flex items-center gap-1 mb-3 text-[11px] text-stone-400">
                    <Flame className="w-3.5 h-3.5 text-amber-500" />
                    <span>Spice Level:</span>
                    <span className="text-amber-400 font-semibold">{item.spiceLevel}</span>
                  </div>
                )}

                {/* Daily plate remaining indicator */}
                {!isSoldOut && (() => {
                  const remaining = getPlateRemaining(item.id);
                  if (remaining === null) return null; // unlimited
                  const cap = item.dailyPlateCount || 30;
                  const pct = Math.max(0, Math.min(100, Math.round((remaining / cap) * 100)));
                  const isLow = pct <= 20 && remaining > 0;
                  return (
                    <div className="mb-3">
                      <div className="flex items-center justify-between mb-1">
                        <span className={`text-[10px] font-semibold ${isLow ? 'text-amber-400' : 'text-stone-500'}`}>
                          {isLow ? '⚠️ ' : ''}{remaining} of {cap} plates left today
                        </span>
                      </div>
                      <div className="w-full h-1.5 bg-stone-800 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full transition-all duration-500 ${
                            pct <= 10 ? 'bg-rose-500' :
                            pct <= 20 ? 'bg-amber-500' :
                            pct <= 50 ? 'bg-amber-400/60' :
                            'bg-emerald-500/60'
                          }`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })()}

                {/* Pre-order status on the selected calendar date */}
                {activeDate && dishStatusOnActiveDate(item.id) !== 'none' && (
                  <div
                    className={`flex items-center gap-1.5 mb-4 text-[11px] rounded-lg px-2 py-1.5 border ${
                      dishStatusOnActiveDate(item.id) === 'available'
                        ? 'text-emerald-300 border-emerald-800/50 bg-emerald-950/30'
                        : 'text-rose-300 border-rose-800/50 bg-rose-950/30'
                    }`}
                  >
                    {dishStatusOnActiveDate(item.id) === 'available' ? (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                    ) : (
                      <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                    )}
                    <span>
                      {dishStatusOnActiveDate(item.id) === 'available'
                        ? `Pre-order available ${prettyDate(activeDate.date)}`
                        : `Not available ${prettyDate(activeDate.date)}`}
                    </span>
                  </div>
                )}

                {/* Inline Quantity Controls or Add Button */}
                <div className="mt-auto">
                  {!allowOrdering ? (
                    onOpenAuth ? (
                      <button
                        onClick={onOpenAuth}
                        className="w-full py-2.5 rounded-xl bg-stone-950 hover:bg-stone-800 text-amber-400/80 hover:text-amber-300 border border-stone-800 hover:border-amber-500/40 text-xs font-semibold text-center transition-all cursor-pointer flex items-center justify-center gap-1.5"
                      >
                        <LogIn className="w-3.5 h-3.5" />
                        Sign in as a customer to order
                      </button>
                    ) : (
                      <div className="w-full py-2.5 rounded-xl bg-stone-950 text-stone-500 border border-stone-800 text-xs font-semibold text-center">
                        Sign in as a customer to order
                      </div>
                    )
                  ) : isSoldOut ? (
                    <div className="space-y-2">
                      {quantityInCart > 0 && (
                        <div className="flex items-center gap-1.5 text-[10px] font-semibold text-rose-400 bg-rose-950/40 border border-rose-800/50 rounded-lg px-2.5 py-1.5">
                          <AlertTriangle className="w-3 h-3" />
                          <span>{quantityInCart} in cart — now sold out</span>
                        </div>
                      )}
                      <button
                        disabled
                        className="w-full py-2.5 rounded-xl bg-stone-950 text-stone-600 border border-stone-800 text-xs font-semibold cursor-not-allowed text-center"
                      >
                        Currently Unavailable
                      </button>
                    </div>
                  ) : quantityInCart > 0 ? (
                    <div className="flex items-center justify-between bg-stone-950 border border-amber-500/40 p-1.5 rounded-xl">
                      <button
                        onClick={() => removeFromCart(item.id)}
                        className="w-8 h-8 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 flex items-center justify-center transition-colors cursor-pointer"
                        title="Reduce quantity"
                      >
                        <Minus className="w-4 h-4" />
                      </button>
                      <span className="font-bold font-mono text-sm text-amber-400 px-3">
                        {quantityInCart} in cart
                      </span>
                      <button
                        onClick={() => addToCart(item)}
                        className="w-8 h-8 rounded-lg bg-amber-500 hover:bg-amber-400 text-stone-950 flex items-center justify-center transition-colors font-bold cursor-pointer shadow-md shadow-amber-500/20"
                        title="Add another"
                      >
                        <Plus className="w-4 h-4 stroke-[3]" />
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => addToCart(item)}
                      className="w-full py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold tracking-wide transition-all cursor-pointer shadow-lg shadow-amber-500/15 flex items-center justify-center gap-2 active:scale-[0.98]"
                    >
                      <Plus className="w-4 h-4 stroke-[2.5]" />
                      <span>Add to Order</span>
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Empty State */}
      {filteredItems.length === 0 && (
        <div className="text-center py-16 bg-stone-900/60 rounded-3xl border border-stone-800 my-8">
          <SearchX className="w-12 h-12 text-stone-600 mx-auto mb-3" />
          <h3 className="text-base font-bold text-stone-200">
            No dishes found matching your criteria
          </h3>
          <p className="text-xs text-stone-500 mt-1 max-w-sm mx-auto">
            Try resetting your diet filter or search term to discover other handcrafted dishes.
          </p>
        </div>
      )}

      {/* Mobile Bottom Sticky Order Bar */}
      {cart.length > 0 && allowOrdering && (
        <div className="fixed bottom-16 md:bottom-0 left-0 w-full bg-stone-950/95 backdrop-blur-xl border-t border-stone-800 z-30 px-4 py-3 md:hidden shadow-2xl">
          {hasUnavailable && (
            <div className="flex items-center gap-1.5 mb-2 text-[10px] font-semibold text-rose-400">
              <AlertTriangle className="w-3 h-3" />
              {unavailableInCart.length} item{unavailableInCart.length !== 1 ? 's' : ''} no longer available
            </div>
          )}
          <div className="flex justify-between items-center mb-2">
            <div className="flex items-center gap-2">
              <ShoppingBag className="w-4 h-4 text-amber-400" />
              <span className="text-xs text-stone-300 font-medium">
                {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'} selected
              </span>
            </div>
            <span className="text-base font-bold text-amber-400 font-mono">
              ₹{totalCartPrice}
            </span>
          </div>
          <button
            onClick={onProceedToPayment}
            className={`w-full py-3 text-xs font-bold rounded-xl flex items-center justify-center gap-2 transition-all cursor-pointer ${
              hasUnavailable
                ? 'bg-amber-500/80 hover:bg-amber-400 text-stone-950 shadow-lg shadow-amber-500/10'
                : 'bg-amber-500 hover:bg-amber-400 text-stone-950 shadow-lg shadow-amber-500/20'
            }`}
          >
            {hasUnavailable ? <AlertTriangle className="w-4 h-4" /> : null}
            <span>{hasUnavailable ? 'Review Cart & Checkout' : 'Proceed to Checkout'}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Desktop Floating Order Pill Bar */}
      {cart.length > 0 && allowOrdering && (
        <div className="hidden md:flex fixed bottom-6 left-1/2 -translate-x-1/2 bg-stone-950/90 backdrop-blur-xl shadow-2xl rounded-full px-6 py-3 items-center space-x-6 z-30 border border-amber-500/30 amber-glow">
          {hasUnavailable && (
            <div className="flex items-center gap-1.5 text-[10px] font-semibold text-rose-400">
              <AlertTriangle className="w-3 h-3" />
              {unavailableInCart.length} unavailable
            </div>
          )}
          {hasUnavailable && <span className="w-px h-5 bg-stone-800"></span>}
          <div className="flex items-center space-x-2">
            <ShoppingBag className="w-5 h-5 text-amber-400" />
            <span className="text-xs font-medium text-stone-200">
              {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'}
            </span>
          </div>
          <span className="w-px h-5 bg-stone-800"></span>
          <span className="text-sm font-bold text-amber-400 font-mono">
            ₹{totalCartPrice}
          </span>
          <button
            onClick={onProceedToPayment}
            className="py-2.5 px-6 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold rounded-full transition-all cursor-pointer shadow-lg shadow-amber-500/20 flex items-center gap-2"
          >
            {hasUnavailable ? <AlertTriangle className="w-4 h-4" /> : null}
            <span>{hasUnavailable ? 'Review Cart & Checkout' : 'Verify Order & Schedule Pickup'}</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  );
};


