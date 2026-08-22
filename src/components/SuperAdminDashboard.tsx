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
  ChefHat,
  Crown,
  BarChart3,
  Activity,
  Sparkles,
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
  onManageRestaurant?: (restaurantId: string) => void;
}

export const SuperAdminDashboard: React.FC<SuperAdminDashboardProps> = ({ onManageRestaurant }) => {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [staffMap, setStaffMap] = useState<Record<string, UserProfile[]>>({});
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const [form, setForm] = useState({
    name: '', description: '', address: '', city: '', cuisine: '',
    phone: '', email: '', logoUrl: '', adminUsername: '', adminEmail: '', adminPassword: '',
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

  useEffect(() => { load().catch(() => {}); }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setMessage(null);
    try {
      await superAdminCreateRestaurant({ ...form, currency: 'INR' });
      setMessage('✅ Restaurant created with admin account!');
      setIsCreateOpen(false);
      setForm({ name: '', description: '', address: '', city: '', cuisine: '', phone: '', email: '', logoUrl: '', adminUsername: '', adminEmail: '', adminPassword: '' });
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

  const activeCount = restaurants.filter(r => r.status === 'ACTIVE').length;
  const totalStaff = Object.values(staffMap).reduce((sum: number, arr: UserProfile[]) => sum + (arr?.length || 0), 0);

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      {/* Hero Header */}
      <div className="relative rounded-3xl overflow-hidden bg-gradient-to-r from-amber-950/80 via-stone-900/90 to-orange-950/40 border border-stone-800 p-6 md:p-8 mb-8 shadow-2xl">
        <div className="absolute -right-10 -top-10 w-60 h-60 bg-amber-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="absolute -left-10 -bottom-10 w-40 h-40 bg-orange-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 text-xs font-semibold mb-3">
              <Crown className="w-3.5 h-3.5" />
              <span>Platform Administration</span>
            </div>
            <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
              <ShieldCheck className="w-8 h-8 text-amber-400" />
              <span>All Restaurants</span>
            </h2>
            <p className="text-xs text-stone-400 mt-1">
              Register restaurants, assign their admins, suspend or remove them — full control of the platform.
            </p>
          </div>
          <button
            onClick={() => setIsCreateOpen(true)}
            className="flex items-center gap-2 bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-stone-950 text-xs font-bold px-5 py-3 rounded-xl transition-all shadow-lg shadow-amber-500/25 hover:shadow-amber-500/40 hover:scale-[1.02] cursor-pointer"
          >
            <Plus className="w-4 h-4 stroke-[3]" />
            Register Restaurant
          </button>
        </div>
      </div>

      {/* Stats Overview */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-stone-800 shadow-xl">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 rounded-lg bg-violet-500/10 flex items-center justify-center">
              <Store className="w-4 h-4 text-violet-400" />
            </div>
            <span className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">Restaurants</span>
          </div>
          <p className="text-2xl font-bold text-white font-mono">{restaurants.length}</p>
          <p className="text-[10px] text-stone-500 mt-0.5">{activeCount} active</p>
        </div>
        <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-stone-800 shadow-xl">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center">
              <Users className="w-4 h-4 text-emerald-400" />
            </div>
            <span className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">Staff</span>
          </div>
          <p className="text-2xl font-bold text-white font-mono">{totalStaff}</p>
          <p className="text-[10px] text-stone-500 mt-0.5">across all restaurants</p>
        </div>
        <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-stone-800 shadow-xl">
          <div className="flex items-center gap-2 mb-2">
            <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center">
              <Activity className="w-4 h-4 text-amber-400" />
            </div>
            <span className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">Platform</span>
          </div>
          <p className="text-2xl font-bold text-emerald-400 font-mono">
            {restaurants.some(r => r.status === 'SUSPENDED') ? 'Mixed' : 'Healthy'}
          </p>
          <p className="text-[10px] text-stone-500 mt-0.5">system status</p>
        </div>
      </div>

      {message && (
        <div className={`mb-6 p-4 rounded-2xl border text-xs font-medium flex items-center gap-2 ${
          message.startsWith('✅')
            ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-300'
            : 'bg-red-950/40 border-red-500/30 text-red-300'
        }`}>
          {message}
        </div>
      )}

      {/* Create Restaurant Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
          <div className="bg-stone-900 border border-stone-700 rounded-3xl max-w-lg w-full p-6 relative my-8 text-stone-100 shadow-2xl">
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
                <input required placeholder="Restaurant name *" value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="col-span-2 px-3 py-2.5 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input placeholder="City" value={form.city}
                  onChange={(e) => setForm({ ...form, city: e.target.value })}
                  className="px-3 py-2.5 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input placeholder="Cuisine" value={form.cuisine}
                  onChange={(e) => setForm({ ...form, cuisine: e.target.value })}
                  className="px-3 py-2.5 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input placeholder="Phone" value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  className="col-span-2 px-3 py-2.5 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input placeholder="Address" value={form.address}
                  onChange={(e) => setForm({ ...form, address: e.target.value })}
                  className="col-span-2 px-3 py-2.5 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input placeholder="Logo URL" value={form.logoUrl}
                  onChange={(e) => setForm({ ...form, logoUrl: e.target.value })}
                  className="col-span-2 px-3 py-2.5 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
              </div>

              <div className="p-4 rounded-xl bg-gradient-to-r from-amber-950/30 to-stone-950 border border-amber-500/20 space-y-3">
                <p className="text-[10px] font-mono uppercase tracking-widest text-amber-400 flex items-center gap-1.5">
                  <Users className="w-3.5 h-3.5" /> First Restaurant Admin Account
                </p>
                <input required placeholder="Admin username *" value={form.adminUsername}
                  onChange={(e) => setForm({ ...form, adminUsername: e.target.value })}
                  className="w-full px-3 py-2.5 bg-stone-900 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input required type="email" placeholder="Admin email *" value={form.adminEmail}
                  onChange={(e) => setForm({ ...form, adminEmail: e.target.value })}
                  className="w-full px-3 py-2.5 bg-stone-900 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
                <input required type="password" placeholder="Admin password *" value={form.adminPassword}
                  onChange={(e) => setForm({ ...form, adminPassword: e.target.value })}
                  className="w-full px-3 py-2.5 bg-stone-900 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100 transition-colors" />
              </div>

              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setIsCreateOpen(false)}
                  className="flex-1 py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-300 font-bold rounded-xl cursor-pointer transition-colors">
                  Cancel
                </button>
                <button type="submit" disabled={isLoading}
                  className="flex-1 py-2.5 bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-stone-950 font-bold rounded-xl disabled:opacity-50 cursor-pointer transition-all">
                  {isLoading ? 'Creating...' : 'Create Restaurant'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Restaurant Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {restaurants.map((r, idx) => {
          const staffCount = staffMap[r.id]?.length || 0;
          const managers = staffMap[r.id]?.filter(s => s.role === 'ROLE_MANAGER').length || 0;
          const chefs = staffMap[r.id]?.filter(s => s.role === 'ROLE_CHEF').length || 0;
          const admins = staffMap[r.id]?.filter(s => s.role === 'ROLE_ADMIN').length || 0;
          const gradients = [
            'from-violet-600/20 to-stone-900/80',
            'from-amber-600/20 to-stone-900/80',
            'from-emerald-600/20 to-stone-900/80',
            'from-blue-600/20 to-stone-900/80',
          ];
          return (
            <div key={r.id} className={`relative overflow-hidden bg-gradient-to-br ${gradients[idx % gradients.length]} backdrop-blur-md rounded-2xl p-5 border shadow-xl transition-all hover:shadow-2xl hover:-translate-y-0.5 ${
              r.status === 'SUSPENDED' ? 'border-rose-800/50 opacity-75' : 'border-stone-800'
            }`}>
              {/* Decorative glow */}
              <div className="absolute -right-8 -top-8 w-32 h-32 bg-amber-500/5 rounded-full blur-2xl pointer-events-none"></div>

              <div className="relative z-10">
                <div className="flex gap-4">
                  <div className="w-16 h-16 rounded-2xl overflow-hidden bg-stone-950 border border-stone-800 shrink-0 flex items-center justify-center shadow-lg">
                    {r.logoUrl ? (
                      <img src={r.logoUrl} alt={r.name} className="w-full h-full object-cover" />
                    ) : (
                      <Store className="w-7 h-7 text-amber-400" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="text-base font-bold font-serif text-stone-100 truncate">{r.name}</h3>
                      <span className={`text-[10px] font-mono font-bold px-2.5 py-0.5 rounded-lg shrink-0 ${
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

                {/* Staff Summary with breakdown */}
                <div className="mt-4 p-3 bg-stone-950/60 rounded-xl border border-stone-800/50">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-[10px] font-mono uppercase tracking-widest text-stone-500 flex items-center gap-1.5">
                      <Users className="w-3 h-3" /> Staff ({staffCount})
                    </p>
                    {staffCount > 0 && (
                      <div className="flex items-center gap-2 text-[9px] text-stone-500">
                        {admins > 0 && <span className="flex items-center gap-0.5"><Crown className="w-2.5 h-2.5 text-amber-400" />{admins}</span>}
                        {managers > 0 && <span className="flex items-center gap-0.5"><Settings className="w-2.5 h-2.5 text-blue-400" />{managers}</span>}
                        {chefs > 0 && <span className="flex items-center gap-0.5"><ChefHat className="w-2.5 h-2.5 text-emerald-400" />{chefs}</span>}
                      </div>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {staffMap[r.id]?.slice(0, 8).map((s) => (
                      <span key={s.id} className="text-[10px] px-2 py-0.5 rounded-md bg-stone-900 border border-stone-800 text-stone-300">
                        {s.username}
                        <span className="text-stone-600 ml-1">{s.role.replace('ROLE_', '')}</span>
                      </span>
                    ))}
                    {staffCount === 0 && (
                      <span className="text-[10px] text-stone-600 italic">No staff added yet</span>
                    )}
                    {staffCount > 8 && (
                      <span className="text-[10px] text-amber-400 font-medium">+{staffCount - 8} more</span>
                    )}
                  </div>
                </div>

                <div className="flex gap-2 mt-4">
                  {onManageRestaurant && (
                    <button
                      onClick={() => onManageRestaurant(r.id)}
                      className="flex-1 py-2.5 rounded-xl text-xs font-bold bg-gradient-to-r from-amber-500/10 to-orange-500/10 hover:from-amber-500/20 hover:to-orange-500/20 text-amber-400 border border-amber-500/30 transition-all cursor-pointer flex items-center justify-center gap-1.5 hover:shadow-lg hover:shadow-amber-500/10"
                    >
                      <Settings className="w-3.5 h-3.5" />
                      Manage
                    </button>
                  )}
                  <button onClick={() => toggleStatus(r)}
                    className={`flex-1 py-2.5 rounded-xl text-xs font-bold border transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                      r.status === 'ACTIVE'
                        ? 'bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border-rose-500/30'
                        : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                    }`}>
                    <Power className="w-3.5 h-3.5" />
                    {r.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                  </button>
                  <button onClick={() => handleDelete(r)}
                    className="py-2.5 px-3 rounded-xl text-xs font-bold bg-stone-950 hover:bg-rose-950/40 text-stone-400 hover:text-rose-400 border border-stone-800 transition-colors cursor-pointer flex items-center justify-center gap-1.5">
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          );
        })}

        {restaurants.length === 0 && (
          <div className="col-span-full text-center py-20 bg-stone-900/60 rounded-3xl border border-stone-800 relative overflow-hidden">
            <div className="absolute -right-10 -top-10 w-40 h-40 bg-amber-500/5 rounded-full blur-3xl pointer-events-none"></div>
            <div className="relative z-10">
              <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-amber-500/10 to-orange-500/10 border border-amber-500/20 flex items-center justify-center mx-auto mb-4">
                <UtensilsCrossed className="w-10 h-10 text-amber-400" />
              </div>
              <h3 className="text-lg font-bold text-stone-200 font-serif">No Restaurants Yet</h3>
              <p className="text-xs text-stone-500 mt-2 max-w-sm mx-auto">
                Get started by registering your first restaurant. You'll be able to manage staff, menus, and orders from there.
              </p>
              <button
                onClick={() => setIsCreateOpen(true)}
                className="mt-6 inline-flex items-center gap-2 bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-stone-950 text-xs font-bold px-5 py-3 rounded-xl transition-all shadow-lg shadow-amber-500/25 cursor-pointer"
              >
                <Plus className="w-4 h-4 stroke-[3]" />
                Register Your First Restaurant
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
