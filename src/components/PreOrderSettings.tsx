import React, { useEffect, useState } from 'react';
import { MenuItem, OperatingHour, PreOrderSettings as Settings, DishAvailabilityView } from '../types';
import {
  CalendarClock,
  Clock3,
  CheckCircle2,
  AlertCircle,
  Save,
  CalendarDays,
  CalendarPlus,
  CalendarX,
  X,
} from 'lucide-react';
import {
  getOperatingHours,
  upsertOperatingHour,
  getPreOrderSettings,
  updatePreOrderSettings,
  staffListMenu,
  getDishAvailability,
  setDishAvailability,
  upsertSlotOverride,
  clearSlotOverride,
} from '../lib/apiClient';

interface PreOrderSettingsProps {
  restaurantId: string;
}

const DAYS = [
  { value: 1, label: 'Monday' },
  { value: 2, label: 'Tuesday' },
  { value: 3, label: 'Wednesday' },
  { value: 4, label: 'Thursday' },
  { value: 5, label: 'Friday' },
  { value: 6, label: 'Saturday' },
  { value: 7, label: 'Sunday' },
];

/** Convert a backend "HH:MM" string to an <input type="time"> value. */
const toTimeInput = (t?: string | null): string => (t ? t.substring(0, 5) : '09:00');
const toHHMM = (t: string): string => t;

export const PreOrderSettings: React.FC<PreOrderSettingsProps> = ({ restaurantId }) => {
  const [hours, setHours] = useState<OperatingHour[]>([]);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);

  const [cutoffTime, setCutoffTime] = useState('09:00');
  const [advanceDays, setAdvanceDays] = useState(7);

  // Per-dish availability editor
  const [selectedDishId, setSelectedDishId] = useState<string>('');
  const [dishAvail, setDishAvail] = useState<DishAvailabilityView | null>(null);
  const [dishAvailDays, setDishAvailDays] = useState<number[]>([]);
  const [overrideDate, setOverrideDate] = useState('');
  const [overrideAction, setOverrideAction] = useState<'OPEN' | 'CLOSE'>('OPEN');

  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const load = async () => {
    setIsLoading(true);
    try {
      const [h, s, m] = await Promise.all([
        getOperatingHours(restaurantId),
        getPreOrderSettings(restaurantId),
        staffListMenu(restaurantId),
      ]);
      setHours(h);
      setSettings(s);
      setMenuItems(m);
      setCutoffTime(toHHMM(s.cutoffTime.substring(0, 5)));
      setAdvanceDays(s.advanceDays ?? 7);
      // Default the dish editor to the first dish (if any).
      if (m.length > 0 && !selectedDishId) selectDish(m[0].id, m);
    } catch (err: any) {
      setMessage({ type: 'err', text: `Failed to load pre-order config: ${err.message}` });
    } finally {
      setIsLoading(false);
    }
  };

  const selectDish = async (menuItemId: string, items?: MenuItem[]) => {
    setSelectedDishId(menuItemId);
    try {
      const view = await getDishAvailability(menuItemId, restaurantId);
      setDishAvail(view);
      setDishAvailDays(view.days || []);
      if (!items) items = menuItems;
      void items;
    } catch (err: any) {
      setMessage({ type: 'err', text: `Failed to load dish availability: ${err.message}` });
    }
  };

  useEffect(() => {
    if (restaurantId) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [restaurantId]);

  const saveHour = async (h: OperatingHour, updates: Partial<OperatingHour>) => {
    try {
      await upsertOperatingHour(
        {
          dayOfWeek: h.dayOfWeek,
          openTime: updates.openTime ?? h.openTime,
          closeTime: updates.closeTime ?? h.closeTime,
          closed: updates.closed ?? h.closed,
        },
        restaurantId
      );
      setMessage({ type: 'ok', text: `✅ ${DAYS[h.dayOfWeek - 1].label} hours saved` });
      const fresh = await getOperatingHours(restaurantId);
      setHours(fresh);
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const saveSettings = async () => {
    try {
      await updatePreOrderSettings({ cutoffTime, advanceDays }, restaurantId);
      setMessage({ type: 'ok', text: '✅ Pre-order cutoff & horizon saved' });
      const fresh = await getPreOrderSettings(restaurantId);
      setSettings(fresh);
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const saveDishDays = async () => {
    if (!selectedDishId) return;
    try {
      if (dishAvailDays.length === 0) {
        setMessage({ type: 'err', text: 'Select at least one weekday, or leave the dish unconfigured (available daily) by removing all days and not saving — for now pick one.' });
        return;
      }
      await setDishAvailability(selectedDishId, dishAvailDays, restaurantId);
      setMessage({ type: 'ok', text: '✅ Dish availability saved' });
      await selectDish(selectedDishId);
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const toggleDay = (day: number) => {
    setDishAvailDays((prev) =>
      prev.includes(day) ? prev.filter((d) => d !== day) : [...prev, day]
    );
  };

  const saveOverride = async () => {
    if (!selectedDishId || !overrideDate) return;
    try {
      await upsertSlotOverride(selectedDishId, overrideDate, overrideAction, restaurantId);
      setMessage({ type: 'ok', text: `✅ ${overrideAction} slot saved for ${overrideDate}` });
      setOverrideDate('');
      await selectDish(selectedDishId);
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const removeOverride = async (date: string) => {
    if (!selectedDishId) return;
    try {
      await clearSlotOverride(selectedDishId, date, restaurantId);
      setMessage({ type: 'ok', text: `✅ Override removed for ${date}` });
      await selectDish(selectedDishId);
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  if (isLoading) {
    return (
      <div className="pt-20 text-center py-20 text-stone-500 text-sm">Loading pre-order configuration...</div>
    );
  }

  return (
    <div className="pt-20 px-4 md:px-8 mb-24 md:mb-12 max-w-[1440px] mx-auto">
      <div className="flex justify-between items-end pb-4 border-b border-stone-800">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2">
            <CalendarClock className="w-7 h-7 text-amber-400" />
            <span>Pre-Order Settings</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Operating hours, cutoff time, and which dishes can be pre-ordered on which days.
          </p>
        </div>
      </div>

      {message && (
        <div className={`mt-4 mb-4 p-3 rounded-xl border text-xs flex items-center gap-2 ${
          message.type === 'ok'
            ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
            : 'bg-rose-500/10 border-rose-500/30 text-rose-400'
        }`}>
          {message.type === 'ok' ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
          {message.text}
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 mt-6">
        {/* ==================== OPERATING HOURS ==================== */}
        <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
          <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-200 mb-1 flex items-center gap-2">
            <Clock3 className="w-4 h-4 text-amber-400" /> Weekly Operating Hours
          </h3>
          <p className="text-[11px] text-stone-500 mb-4">
            A day marked <b>closed</b> or closing at/before <b>14:00</b> (2nd half closed) blocks
            <b> all pre-orders</b> for that day. On open days, pickup must fall within these hours.
          </p>
          <div className="space-y-2">
            {DAYS.map((d) => {
              const h = hours.find((x) => x.dayOfWeek === d.value);
              const open = h?.openTime ? toTimeInput(h.openTime) : '09:00';
              const close = h?.closeTime ? toTimeInput(h.closeTime) : '23:00';
              const closed = !!h?.closed;
              return (
                <div key={d.value} className="flex items-center gap-2 bg-stone-950 rounded-xl border border-stone-800 p-2.5">
                  <span className="w-20 text-xs font-semibold text-stone-300 shrink-0">{d.label}</span>
                  <input
                    type="time"
                    value={open}
                    disabled={closed}
                    onChange={(e) => saveHour(h || { restaurantId, dayOfWeek: d.value, closed: false }, { openTime: toHHMM(e.target.value), closeTime: close })}
                    className="flex-1 bg-stone-900 border border-stone-800 rounded-lg px-2 py-1.5 text-xs text-stone-200 disabled:opacity-40"
                  />
                  <span className="text-stone-600 text-xs">→</span>
                  <input
                    type="time"
                    value={close}
                    disabled={closed}
                    onChange={(e) => saveHour(h || { restaurantId, dayOfWeek: d.value, closed: false }, { closeTime: toHHMM(e.target.value), openTime: open })}
                    className="flex-1 bg-stone-900 border border-stone-800 rounded-lg px-2 py-1.5 text-xs text-stone-200 disabled:opacity-40"
                  />
                  <label className="flex items-center gap-1.5 text-[10px] text-stone-400 cursor-pointer whitespace-nowrap">
                    <input
                      type="checkbox"
                      checked={closed}
                      onChange={(e) => saveHour(h || { restaurantId, dayOfWeek: d.value, closed: false }, { closed: e.target.checked, openTime: open, closeTime: close })}
                      className="accent-amber-500"
                    />
                    Closed
                  </label>
                </div>
              );
            })}
          </div>
          {hours.length === 0 && (
            <p className="text-[11px] text-stone-500 mt-3">
              No hours configured yet — unconfigured days are treated as open until you set them
              (the daily reminder will nudge you).
            </p>
          )}
        </div>

        <div className="space-y-6">
          {/* ==================== CUTOFF + HORIZON ==================== */}
          <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
            <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-200 mb-4 flex items-center gap-2">
              <CalendarClock className="w-4 h-4 text-amber-400" /> Cutoff & Horizon
            </h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[11px] font-semibold text-stone-400 mb-1">Pre-Order Cutoff Time</label>
                <input
                  type="time"
                  value={cutoffTime}
                  onChange={(e) => setCutoffTime(e.target.value)}
                  className="w-full bg-stone-950 border border-stone-800 rounded-xl px-3 py-2 text-xs text-stone-100 focus:border-amber-500 focus:outline-none"
                />
                <p className="text-[10px] text-stone-500 mt-1">
                  This restaurant's cutoff: orders for a date close at this time on the day before (D-1),
                  business timezone. It must not be after the restaurant's opening time for that day.
                </p>
              </div>
              <div>
                <label className="block text-[11px] font-semibold text-stone-400 mb-1">Max Advance Days</label>
                <input
                  type="number"
                  min={1}
                  max={30}
                  value={advanceDays}
                  onChange={(e) => setAdvanceDays(parseInt(e.target.value) || 7)}
                  className="w-full bg-stone-950 border border-stone-800 rounded-xl px-3 py-2 text-xs text-stone-100 focus:border-amber-500 focus:outline-none font-mono"
                />
                <p className="text-[10px] text-stone-500 mt-1">How many days ahead customers may pre-order.</p>
              </div>
            </div>
            <button
              onClick={saveSettings}
              className="mt-4 w-full flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
            >
              <Save className="w-4 h-4" /> Save Cutoff & Horizon
            </button>
          </div>

          {/* ==================== DISH AVAILABILITY ==================== */}
          <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
            <h3 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-200 mb-4 flex items-center gap-2">
              <CalendarDays className="w-4 h-4 text-amber-400" /> Dish Pre-Order Availability
            </h3>
            <label className="block text-[11px] font-semibold text-stone-400 mb-1">Select Dish</label>
            <select
              value={selectedDishId}
              onChange={(e) => selectDish(e.target.value)}
              className="w-full bg-stone-950 border border-stone-800 rounded-xl px-3 py-2 text-xs text-stone-100 focus:border-amber-500 focus:outline-none mb-4"
            >
              {menuItems.map((m) => (
                <option key={m.id} value={m.id}>{m.title}</option>
              ))}
            </select>

            {dishAvail && (
              <>
                <p className="text-[11px] text-stone-500 mb-2">
                  Which weekdays is this dish cooked? Leave all unchecked = available every day.
                </p>
                <div className="grid grid-cols-7 gap-1.5 mb-4">
                  {DAYS.map((d) => {
                    const active = dishAvailDays.includes(d.value);
                    return (
                      <button
                        key={d.value}
                        type="button"
                        onClick={() => toggleDay(d.value)}
                        className={`py-2 rounded-xl text-[10px] font-bold border transition-all cursor-pointer ${
                          active
                            ? 'bg-amber-500/15 text-amber-400 border-amber-500/50'
                            : 'bg-stone-950 text-stone-500 border-stone-800 hover:text-stone-300'
                        }`}
                      >
                        {d.label.substring(0, 3)}
                      </button>
                    );
                  })}
                </div>
                <button
                  onClick={saveDishDays}
                  className="w-full flex items-center justify-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
                >
                  <Save className="w-4 h-4" /> Save Weekly Schedule
                </button>

                {/* Slot overrides */}
                <div className="mt-5 pt-4 border-t border-stone-800">
                  <h4 className="text-[11px] font-bold uppercase tracking-wider text-stone-400 mb-3 flex items-center gap-1.5">
                    <CalendarPlus className="w-3.5 h-3.5" /> Open / Close a Specific Date
                  </h4>
                  <div className="flex gap-2">
                    <input
                      type="date"
                      value={overrideDate}
                      onChange={(e) => setOverrideDate(e.target.value)}
                      className="flex-1 bg-stone-950 border border-stone-800 rounded-xl px-3 py-2 text-xs text-stone-100 focus:border-amber-500 focus:outline-none"
                    />
                    <select
                      value={overrideAction}
                      onChange={(e) => setOverrideAction(e.target.value as 'OPEN' | 'CLOSE')}
                      className="bg-stone-950 border border-stone-800 rounded-xl px-2 py-2 text-xs text-stone-100"
                    >
                      <option value="OPEN">Open</option>
                      <option value="CLOSE">Close</option>
                    </select>
                    <button
                      onClick={saveOverride}
                      className="bg-violet-600 hover:bg-violet-500 text-white text-xs font-bold px-3 py-2 rounded-xl cursor-pointer"
                    >
                      Save
                    </button>
                  </div>
                  <p className="text-[10px] text-stone-500 mt-1.5">
                    Precedence: explicit Close &gt; Open &gt; weekly schedule. Restaurant closure always wins.
                  </p>
                  {dishAvail.overrides.length > 0 && (
                    <div className="mt-3 space-y-1.5">
                      {dishAvail.overrides.map((o) => (
                        <div key={o.date} className="flex items-center justify-between bg-stone-950 rounded-lg border border-stone-800 px-3 py-1.5">
                          <span className={`text-[11px] font-mono ${o.action === 'OPEN' ? 'text-emerald-400' : 'text-rose-400'}`}>
                            {o.date} — {o.action}
                          </span>
                          <button onClick={() => removeOverride(o.date)} className="text-stone-500 hover:text-rose-400 cursor-pointer">
                            <X className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            )}
            {!dishAvail && <p className="text-[11px] text-stone-500">Select a dish to manage its availability.</p>}
          </div>
        </div>
      </div>
    </div>
  );
};
