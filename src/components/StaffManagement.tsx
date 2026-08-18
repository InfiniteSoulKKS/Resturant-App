import React, { useEffect, useState } from 'react';
import {
  UserPlus,
  Users,
  ChefHat,
  ClipboardCheck,
  Power,
  X,
  CheckCircle2,
  AlertCircle,
  KeyRound,
} from 'lucide-react';
import { UserProfile } from '../types';
import { addStaff, listStaff, setStaffEnabled } from '../lib/apiClient';
import { parseRoles, ROLE_LABELS } from '../lib/roles';

interface StaffManagementProps {
  restaurantId: string;
  restaurantName?: string;
}

const ROLE_META: Record<string, { icon: React.ReactNode; color: string; label: string }> = {
  ROLE_MANAGER: {
    icon: <ClipboardCheck className="w-4 h-4 text-amber-400" />,
    color: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
    label: 'Manager',
  },
  ROLE_CHEF: {
    icon: <ChefHat className="w-4 h-4 text-rose-400" />,
    color: 'bg-rose-500/10 text-rose-400 border-rose-500/30',
    label: 'Chef',
  },
  ROLE_ADMIN: {
    icon: <Users className="w-4 h-4 text-sky-400" />,
    color: 'bg-sky-500/10 text-sky-400 border-sky-500/30',
    label: 'Admin',
  },
};

export const StaffManagement: React.FC<StaffManagementProps> = ({ restaurantId, restaurantName }) => {
  const [staff, setStaff] = useState<UserProfile[]>([]);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    phone: '',
    roles: ['ROLE_MANAGER'] as string[],
  });

  const toggleRole = (role: string) => {
    setForm((prev) => ({
      ...prev,
      roles: prev.roles.includes(role)
        ? prev.roles.filter((r) => r !== role)
        : [...prev.roles, role],
    }));
  };

  const load = async () => {
    const data = await listStaff(restaurantId);
    setStaff(data);
  };

  useEffect(() => {
    load().catch(() => {});
  }, [restaurantId]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setMessage(null);
    try {
      if (!form.email.trim() && !form.phone.trim()) {
        setMessage({ type: 'err', text: '❌ Provide at least one of Email or Phone — staff without an email can be added with just a phone number.' });
        setIsLoading(false);
        return;
      }
      const roleCsv = form.roles.join(',');
      await addStaff({ ...form, role: roleCsv, restaurantId });
      const label = form.roles.map((r) => ROLE_LABELS[r] || r.replace('ROLE_', '')).join(' + ');
      setMessage({ type: 'ok', text: `✅ ${label} account created for ${form.username}. They can log in with their credentials.` });
      setIsFormOpen(false);
      setForm({ username: '', email: '', password: '', phone: '', roles: ['ROLE_MANAGER'] });
      await load();
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    } finally {
      setIsLoading(false);
    }
  };

  const toggleEnabled = async (s: UserProfile) => {
    await setStaffEnabled(s.id, s.enabled !== false ? false : true, restaurantId);
    await load();
  };

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-8">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
            <Users className="w-8 h-8 text-amber-400" />
            <span>Staff Management</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            {restaurantName ? `Managing staff for ${restaurantName}.` : 'Add managers and chefs to run the kitchen.'}
            {' '}Staff sign in with the credentials you create below.
          </p>
        </div>
        <button
          onClick={() => setIsFormOpen(true)}
          className="flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold px-4 py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
        >
          <UserPlus className="w-4 h-4 stroke-[3]" />
          Add Manager / Chef
        </button>
      </div>

      {message && (
        <div className={`mb-6 p-3 rounded-xl border text-xs flex items-center gap-2 ${
          message.type === 'ok'
            ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
            : 'bg-rose-500/10 border-rose-500/30 text-rose-400'
        }`}>
          {message.type === 'ok' ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
          {message.text}
        </div>
      )}

      {/* Staff grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {staff.map((s) => {
          const roles = parseRoles(s.role);
          const enabled = s.enabled !== false;
          return (
            <div key={s.id} className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl flex flex-col gap-3">
              <div className="flex items-center gap-3">
                <div className="w-11 h-11 bg-amber-500 text-stone-950 font-bold rounded-xl flex items-center justify-center uppercase shadow-md shadow-amber-500/20">
                  {s.username.charAt(0)}
                </div>
                <div className="min-w-0 flex-1">
                  <h4 className="text-sm font-bold text-stone-100 truncate">{s.username}</h4>
                  <p className="text-[11px] text-stone-400 truncate">{s.email || s.phone || 'No email or phone'}</p>
                </div>
                <div className="flex flex-col items-end gap-1">
                  {roles.map((r) => {
                    const meta = ROLE_META[r] || ROLE_META.ROLE_MANAGER;
                    return (
                      <span key={r} className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded-lg border flex items-center gap-1 ${meta.color}`}>
                        {meta.icon}
                        {meta.label}
                      </span>
                    );
                  })}
                </div>
              </div>
              <p className="text-[11px] text-stone-500 flex items-center gap-1.5">
                <KeyRound className="w-3 h-3" />
                {s.phone || 'No phone'} · {enabled ? 'Active' : 'Disabled'}
              </p>
              <button
                onClick={() => toggleEnabled(s)}
                className={`mt-auto py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                  enabled
                    ? 'bg-stone-950 hover:bg-rose-950/40 text-stone-400 hover:text-rose-400 border-stone-800'
                    : 'bg-emerald-500/10 hover:bg-emerald-500/20 text-emerald-400 border-emerald-500/30'
                }`}
              >
                <Power className="w-3.5 h-3.5" />
                {enabled ? 'Disable Account' : 'Enable Account'}
              </button>
            </div>
          );
        })}
      </div>

      {staff.length === 0 && (
        <div className="text-center py-20 bg-stone-900/60 rounded-3xl border border-stone-800">
          <Users className="w-10 h-10 text-stone-600 mx-auto mb-2" />
          <p className="text-sm text-stone-300">No staff yet. Add your first manager or chef!</p>
        </div>
      )}

      {/* Add staff modal */}
      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
          <div className="bg-stone-900 border border-stone-700 rounded-3xl max-w-md w-full p-6 relative my-8 text-stone-100">
            <div className="flex justify-between items-center pb-4 border-b border-stone-800">
              <h3 className="text-base font-bold font-serif flex items-center gap-2">
                <UserPlus className="w-5 h-5 text-amber-400" />
                Add Staff Account
              </h3>
              <button onClick={() => setIsFormOpen(false)} className="text-stone-400 hover:text-stone-100 p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleAdd} className="mt-4 space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-2 p-1 bg-stone-950 rounded-xl border border-stone-800">
                {(['ROLE_MANAGER', 'ROLE_CHEF'] as const).map((role) => {
                  const active = form.roles.includes(role);
                  return (
                    <button
                      key={role}
                      type="button"
                      onClick={() => toggleRole(role)}
                      className={`py-2 rounded-lg text-xs font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                        active
                          ? 'bg-amber-500 text-stone-950'
                          : 'text-stone-400 hover:text-stone-100'
                      }`}
                    >
                      <span className={`w-2 h-2 rounded-sm border ${active ? 'bg-stone-950 border-stone-950' : 'border-stone-600'}`} />
                      {role === 'ROLE_MANAGER' ? 'Manager' : 'Chef'}
                    </button>
                  );
                })}
              </div>
              <p className="text-[10px] text-stone-500 -mt-1">
                Tip: select both to give one person shared Manager + Chef responsibilities.
              </p>
              {form.roles.length === 0 && (
                <p className="text-[10px] text-rose-400">Select at least one role.</p>
              )}

              <input
                required placeholder="Username *" value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
                className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
              <input
                type="email" placeholder="Email (optional — or use phone below)" value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
              <input
                required type="password" placeholder="Password *" value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
              <input
                placeholder="Phone (needed if no email)" value={form.phone}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
                className="w-full px-3 py-2 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl text-stone-100" />
              <p className="text-[10px] text-stone-500 -mt-1">
                Provide at least one of <span className="text-amber-400">Email</span> or <span className="text-amber-400">Phone</span>. Staff without an email ID can be added with just a phone number.
              </p>

              <div className="flex gap-2 pt-1">
                <button type="button" onClick={() => setIsFormOpen(false)}
                  className="flex-1 py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-300 font-bold rounded-xl cursor-pointer">
                  Cancel
                </button>
                <button type="submit" disabled={isLoading || form.roles.length === 0}
                  className="flex-1 py-2.5 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold rounded-xl disabled:opacity-50 cursor-pointer">
                  {isLoading ? 'Creating...' : 'Create Account'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
