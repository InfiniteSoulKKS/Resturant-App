import React, { useEffect, useState } from 'react';
import {
  Building2,
  Plus,
  X,
  MapPin,
  Phone,
  Mail,
  Store,
  ShieldCheck,
  Power,
  Trash2,
  Users,
  UtensilsCrossed,
  Settings,
} from 'lucide-react';
import { Restaurant, UserProfile } from '../types';
import {
  superAdminListRestaurants,
  superAdminCreateRestaurant,
  superAdminUpdateRestaurant,
  superAdminDeleteRestaurant,
  listStaff,
} from '../lib/apiClient';

interface SuperAdminDashboardProps {
  /** Super admin picks a restaurant to manage (menu, pre-orders, staff...). */
  onManageRestaurant?: (restaurantId: string) => void;
}

export const SuperAdminDashboard: React.FC<SuperAdminDashboardProps> = ({ onManageRestaurant }) => {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [staffMap, setStaffMap] = useState<Record<string, UserProfile[]>>({});
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Create form
  const [form, setForm] = useState({
    name: '',
    description: '',
    address: '',
    city: '',
    cuisine: '',
    phone: '',
    email: '',
    logoUrl: '',
    adminUsername: '',
    adminEmail: '',
    adminPassword: '',
  });

  const load = async () => {
    const list = await superAdminListRestaurants();
    setRestaurants(list);
    const map: Record<string, UserProfile[]> = {};
    for (const r of list) {
      try {
        map[r.id] = await listStaff(r.id);
      } catch {
        map[r.id] = [];
      }
    }
    setStaffMap(map);
  };

  useEffect(() => {
    load().catch(() => {});
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setMessage(null);
    try {
      await superAdminCreateRestaurant({
        ...form,
        currency: 'INR',
      });
      setMessage('✅ Restaurant created with admin account!');
      setIsCreateOpen(false);
      setForm({
        name: '', description: '', address: '', city: '', cuisine: '',
        phone: '', email: '', logoUrl: '', adminUsername: '', adminEmail: '', adminPassword: '',
      });
      await load();
    } catch (err: any) {
      setMessage(`❌ ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const toggleStatus = async (r: Restaurant) => {
    const next = r.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    await superAdminUpdateRestaurant(r.id, { status: next });
    await load();
  };

  const handleDelete = async (r: Restaurant) => {
    if (confirm(`Delete "${r.name}" and all its data? This cannot be undone.`)) {
      await superAdminDeleteRestaurant(r.id);
      await load();
    }
  };

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-8">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
            <ShieldCheck className="w-8 h-8 text-amber-400" />
            <span>Platform Admin · All Restaurants</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Register restaurants, assign their admins, suspend or remove them — full control of the platform.
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold px-4 py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
        >
          <Plus className="w-4 h-4 stroke-[3]" />
          Register Restaurant
        </button>
      </div>

      {message && (
        <div className="mb-6 p-3 rounded-xl bg-stone-900 border border-stone-700 text-xs text-stone-200">
          {message}
        </div>
      )}

      {/* Create Restaurant Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
          <div className="bg-stone-900 border border-stone-700 rounded-3xl max-w-lg w-full p-6 relative my-8 text-stone-100">
            <div className="flex justify-between items-center pb-4 border-b border-stone-800">
              <h3 className="text-base font-bold font-serif flex items-center gap-2">
                <Building2 className="w-5 h-5 text-amber-400" />
                Register New Restaurant
              </h3>
              <button onClick={() => setIsCreateOpen(false)} className="text-stone-400 hover:text-stone-100 p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreate} className="mt-4 space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <input
                  required placeholder="Restaurant name *" value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="col-span-2 px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100"
                />
                <input placeholder="City" value={form.city}
                  onChange={(e) => setForm({ ...form, city: e.target.value })}
                  className="px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
                <input placeholder="Cuisine" value={form.cuisine}
                  onChange={(e) => setForm({ ...form, cuisine: e.target.value })}
                  className="px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
                <input placeholder="Phone" value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  className="col-span-2 px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
                <input placeholder="Address" value={form.address}
                  onChange={(e) => setForm({ ...form, address: e.target.value })}
                  className="col-span-2 px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
                <input placeholder="Logo URL" value={form.logoUrl}
                  onChange={(e) => setForm({ ...form, logoUrl: e.target.value })}
                  className="col-span-2 px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
              </div>

              <div className="p-3 rounded-xl bg-stone-950 border border-amber-500/20 space-y-3">
                <p className="text-[10px] font-mono uppercase tracking-widest text-amber-400 flex items-center gap-1.5">
                  <Users className="w-3.5 h-3.5" /> First Restaurant Admin Account
                </p>
                <input
                  required placeholder="Admin username *" value={form.adminUsername}
                  onChange={(e) => setForm({ ...form, adminUsername: e.target.value })}
                  className="w-full px-3 py-2 bg-stone-900 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
                <input
                  required type="email" placeholder="Admin email *" value={form.adminEmail}
                  onChange={(e) => setForm({ ...form, adminEmail: e.target.value })}
                  className="w-full px-3 py-2 bg-stone-900 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
                <input
                  required type="password" placeholder="Admin password *" value={form.adminPassword}
                  onChange={(e) => setForm({ ...form, adminPassword: e.target.value })}
                  className="w-full px-3 py-2 bg-stone-900 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
              </div>

              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setIsCreateOpen(false)}
                  className="flex-1 py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-300 font-bold rounded-xl cursor-pointer">
                  Cancel
                </button>
                <button type="submit" disabled={isLoading}
                  className="flex-1 py-2.5 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold rounded-xl disabled:opacity-50 cursor-pointer">
                  {isLoading ? 'Creating...' : 'Create Restaurant'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Restaurant Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {restaurants.map((r) => (
          <div key={r.id} className={`bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border shadow-xl transition-all ${
            r.status === 'SUSPENDED' ? 'border-rose-800/50 opacity-75' : 'border-stone-800'
          }`}>
            <div className="flex gap-4">
              <div className="w-16 h-16 rounded-2xl overflow-hidden bg-stone-950 border border-stone-800 shrink-0 flex items-center justify-center">
                {r.logoUrl ? (
                  <img src={r.logoUrl} alt={r.name} className="w-full h-full object-cover" />
                ) : (
                  <Store className="w-7 h-7 text-amber-400" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-2">
                  <h3 className="text-base font-bold font-serif text-stone-100 truncate">{r.name}</h3>
                  <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded-lg shrink-0 ${
                    r.status === 'ACTIVE'
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                      : 'bg-rose-500/10 text-rose-400 border border-rose-500/30'
                  }`}>
                    {r.status}
                  </span>
                </div>
                <p className="text-[11px] text-stone-400 mt-1 flex items-center gap-1.5">
                  <MapPin className="w-3 h-3 text-amber-400" /> {r.city || '—'} · {r.cuisine || '—'}
                </p>
                <p className="text-[11px] text-stone-400 flex items-center gap-1.5 mt-0.5">
                  <Phone className="w-3 h-3 text-stone-500" /> {r.phone || '—'}
                  <span className="mx-1 text-stone-700">|</span>
                  <Mail className="w-3 h-3 text-stone-500" /> {r.email || '—'}
                </p>
              </div>
            </div>

            {/* Staff summary */}
            <div className="mt-4 p-3 bg-stone-950 rounded-xl border border-stone-800">
              <p className="text-[10px] font-mono uppercase tracking-widest text-stone-500 mb-2 flex items-center gap-1.5">
                <Users className="w-3 h-3" /> Staff ({staffMap[r.id]?.length || 0})
              </p>
              <div className="flex flex-wrap gap-1.5">
                {staffMap[r.id]?.slice(0, 6).map((s) => (
                  <span key={s.id} className="text-[10px] px-2 py-0.5 rounded-md bg-stone-900 border border-stone-800 text-stone-300">
                    {s.username}
                    <span className="text-stone-600 ml-1">{s.role.replace('ROLE_', '')}</span>
                  </span>
                ))}
                {(staffMap[r.id]?.length || 0) === 0 && (
                  <span className="text-[10px] text-stone-600">No staff added yet</span>
                )}
              </div>
            </div>

            <div className="flex gap-2 mt-4">
              {onManageRestaurant && (
                <button
                  onClick={() => onManageRestaurant(r.id)}
                  className="flex-1 py-2 rounded-xl text-xs font-bold bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 transition-all cursor-pointer flex items-center justify-center gap-1.5"
                >
                  <Settings className="w-3.5 h-3.5" />
                  Manage
                </button>
              )}
              <button onClick={() => toggleStatus(r)}
                className={`flex-1 py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                  r.status === 'ACTIVE'
                    ? 'bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border-rose-500/30'
                    : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                }`}>
                <Power className="w-3.5 h-3.5" />
                {r.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
              </button>
              <button onClick={() => handleDelete(r)}
                className="flex-1 py-2 rounded-xl text-xs font-bold bg-stone-950 hover:bg-rose-950/40 text-stone-400 hover:text-rose-400 border border-stone-800 transition-colors cursor-pointer flex items-center justify-center gap-1.5">
                <Trash2 className="w-3.5 h-3.5" />
                Remove
              </button>
            </div>
          </div>
        ))}

        {restaurants.length === 0 && (
          <div className="col-span-full text-center py-20 bg-stone-900/60 rounded-3xl border border-stone-800">
            <UtensilsCrossed className="w-10 h-10 text-stone-600 mx-auto mb-2" />
            <p className="text-sm text-stone-300">No restaurants registered yet.</p>
          </div>
        )}
      </div>
    </div>
  );
};
