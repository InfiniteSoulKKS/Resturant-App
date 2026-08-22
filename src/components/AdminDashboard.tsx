import React, { useEffect, useState } from 'react';
import {
  LayoutDashboard,
  Sparkles,
  Users,
  UserCog,
  ShoppingCart,
  CalendarClock,
  ChefHat,
  Settings,
  TrendingUp,
  Clock,
  Package,
  CheckCircle2,
  AlertTriangle,
  Crown,
  Zap,
  Target,
  BarChart3,
  Activity,
  UtensilsCrossed,
  ArrowRight,
  RefreshCw,
  IndianRupee,
} from 'lucide-react';
import { Restaurant } from '../types';
import {
  getDashboardSummary,
  listStaff,
  listCustomerMembers,
  type DashboardSummary,
} from '../lib/apiClient';

interface AdminDashboardProps {
  restaurantId: string | null;
  restaurantName?: string;
  restaurant?: Restaurant;
  onNavigate: (tab: string) => void;
}

interface QuickAction {
  label: string;
  description: string;
  icon: React.ReactNode;
  tab: string;
  gradient: string;
  iconBg: string;
}

export const AdminDashboard: React.FC<AdminDashboardProps> = ({
  restaurantId,
  restaurantName,
  restaurant,
  onNavigate,
}) => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [staffCount, setStaffCount] = useState(0);
  const [memberCount, setMemberCount] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!restaurantId) return;
    const load = async () => {
      setLoading(true);
      try {
        const [s, staff, members] = await Promise.all([
          getDashboardSummary(restaurantId),
          listStaff(restaurantId),
          listCustomerMembers(restaurantId),
        ]);
        setSummary(s);
        setStaffCount(staff.length);
        setMemberCount(members.length);
      } catch (err) {
        console.error('Failed to load admin dashboard:', err);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [restaurantId]);

  const quickActions: QuickAction[] = [
    {
      label: 'Menu Management',
      description: 'Add, edit, reorder menu items & pricing',
      icon: <Sparkles className="w-5 h-5" />,
      tab: 'menu_management',
      gradient: 'from-amber-500/15 to-orange-500/10',
      iconBg: 'bg-amber-500/15 text-amber-400 border-amber-500/30',
    },
    {
      label: 'Orders',
      description: 'View & manage today\'s orders and pickups',
      icon: <ShoppingCart className="w-5 h-5" />,
      tab: 'orders',
      gradient: 'from-blue-500/15 to-cyan-500/10',
      iconBg: 'bg-blue-500/15 text-blue-400 border-blue-500/30',
    },
    {
      label: 'Staff Management',
      description: 'Invite, assign roles, manage your team',
      icon: <Users className="w-5 h-5" />,
      tab: 'staff_management',
      gradient: 'from-violet-500/15 to-purple-500/10',
      iconBg: 'bg-violet-500/15 text-violet-400 border-violet-500/30',
    },
    {
      label: 'Customer Members',
      description: `${memberCount} customers joined — manage memberships`,
      icon: <UserCog className="w-5 h-5" />,
      tab: 'customer_memberships',
      gradient: 'from-sky-500/15 to-teal-500/10',
      iconBg: 'bg-sky-500/15 text-sky-400 border-sky-500/30',
    },
    {
      label: 'Kitchen & Stock',
      description: 'Ingredient planning & stock levels',
      icon: <ChefHat className="w-5 h-5" />,
      tab: 'chef_prep',
      gradient: 'from-emerald-500/15 to-green-500/10',
      iconBg: 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30',
    },
    {
      label: 'Pre-Order Settings',
      description: 'Operating hours, tables, time slots',
      icon: <CalendarClock className="w-5 h-5" />,
      tab: 'preorder_settings',
      gradient: 'from-rose-500/15 to-pink-500/10',
      iconBg: 'bg-rose-500/15 text-rose-400 border-rose-500/30',
    },
    {
      label: 'Analytics Dashboard',
      description: 'Revenue, payments, and reconciliation',
      icon: <BarChart3 className="w-5 h-5" />,
      tab: 'dashboard',
      gradient: 'from-indigo-500/15 to-violet-500/10',
      iconBg: 'bg-indigo-500/15 text-indigo-400 border-indigo-500/30',
    },
  ];

  const completionRate = summary
    ? summary.totalOrders > 0
      ? Math.round((summary.completed / summary.totalOrders) * 100)
      : 0
    : 0;

  if (loading) {
    return (
      <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
        <div className="flex flex-col items-center justify-center h-64 gap-4">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-amber-500/20 to-orange-500/20 border border-amber-500/30 flex items-center justify-center">
            <RefreshCw className="w-6 h-6 text-amber-400 animate-spin" />
          </div>
          <p className="text-sm text-stone-400 font-medium">Loading admin dashboard...</p>
        </div>
      </div>
    );
  }

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
              <span>Restaurant Admin</span>
            </div>
            <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
              <LayoutDashboard className="w-8 h-8 text-amber-400" />
              <span>{restaurantName || 'Admin Dashboard'}</span>
            </h2>
            <p className="text-xs text-stone-400 mt-1">
              Full control of your restaurant — menu, staff, customers, orders, and settings.
            </p>
          </div>

          {/* Quick Completion Ring */}
          {summary && (
            <div className="hidden md:flex items-center gap-3 bg-stone-900/60 rounded-2xl border border-stone-800 px-4 py-2.5">
              <div className="relative w-12 h-12">
                <svg className="w-12 h-12 -rotate-90" viewBox="0 0 36 36">
                  <path
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                    fill="none"
                    stroke="rgb(39 39 42)"
                    strokeWidth="3"
                  />
                  <path
                    d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                    fill="none"
                    stroke="rgb(245 158 11)"
                    strokeWidth="3"
                    strokeDasharray={`${completionRate}, 100`}
                    className="transition-all duration-1000"
                  />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="text-xs font-bold text-amber-400">{completionRate}%</span>
                </div>
              </div>
              <div>
                <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">Completion</p>
                <p className="text-sm font-bold text-stone-200">{summary.completed} / {summary.totalOrders}</p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Today's Stats */}
      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <StatCard
            label="Total Orders"
            value={summary.totalOrders}
            icon={<Package className="w-5 h-5" />}
            gradient="from-violet-600 to-purple-700"
          />
          <StatCard
            label="Revenue"
            value={`₹${summary.revenue.toLocaleString()}`}
            icon={<TrendingUp className="w-5 h-5" />}
            gradient="from-emerald-600 to-green-700"
          />
          <StatCard
            label="Pending"
            value={summary.pending}
            icon={<Clock className="w-5 h-5" />}
            gradient="from-blue-500 to-blue-600"
            pulse={summary.pending > 0}
          />
          <StatCard
            label="Ready"
            value={summary.ready}
            icon={<Zap className="w-5 h-5" />}
            gradient="from-cyan-500 to-blue-600"
          />
        </div>
      )}

      {/* Secondary Stats Row */}
      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <MiniStat label="Preparing" value={summary.preparing} color="amber" icon={<ChefHat className="w-4 h-4" />} />
          <MiniStat label="Completed" value={summary.completed} color="emerald" icon={<Target className="w-4 h-4" />} />
          <MiniStat label="Staff" value={staffCount} color="violet" icon={<Users className="w-4 h-4" />} />
          <MiniStat label="Members" value={memberCount} color="sky" icon={<UserCog className="w-4 h-4" />} />
        </div>
      )}

      {/* Alerts */}
      {summary && (summary.ingredientShortages > 0 || summary.soldOutDishes > 0 || summary.delayed > 0) && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
          {summary.ingredientShortages > 0 && (
            <div className="bg-red-950/30 border border-red-500/30 rounded-2xl p-4 flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-red-500/10 border border-red-500/30 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5 text-red-400" />
              </div>
              <div>
                <p className="text-sm font-bold text-red-400">{summary.ingredientShortages}</p>
                <p className="text-[10px] text-stone-400">Ingredient Shortages</p>
              </div>
            </div>
          )}
          {summary.soldOutDishes > 0 && (
            <div className="bg-amber-950/30 border border-amber-500/30 rounded-2xl p-4 flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center">
                <AlertTriangle className="w-5 h-5 text-amber-400" />
              </div>
              <div>
                <p className="text-sm font-bold text-amber-400">{summary.soldOutDishes}</p>
                <p className="text-[10px] text-stone-400">Sold-Out Dishes</p>
              </div>
            </div>
          )}
          {summary.delayed > 0 && (
            <div className="bg-orange-950/30 border border-orange-500/30 rounded-2xl p-4 flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-orange-500/10 border border-orange-500/30 flex items-center justify-center">
                <Clock className="w-5 h-5 text-orange-400" />
              </div>
              <div>
                <p className="text-sm font-bold text-orange-400">{summary.delayed}</p>
                <p className="text-[10px] text-stone-400">Delayed Orders</p>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Quick Actions Grid */}
      <div className="mb-8">
        <h3 className="text-sm font-bold text-stone-300 mb-4 flex items-center gap-2">
          <Zap className="w-4 h-4 text-amber-400" />
          Quick Actions
        </h3>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {quickActions.map((action) => (
            <button
              key={action.tab}
              onClick={() => onNavigate(action.tab)}
              className={`group relative overflow-hidden bg-gradient-to-br ${action.gradient} backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl text-left transition-all hover:shadow-2xl hover:-translate-y-0.5 hover:border-stone-700 cursor-pointer`}
            >
              <div className="absolute -right-6 -top-6 w-24 h-24 bg-white/5 rounded-full blur-2xl pointer-events-none group-hover:bg-white/10 transition-colors"></div>
              <div className="relative z-10">
                <div className={`w-10 h-10 rounded-xl border flex items-center justify-center mb-3 ${action.iconBg}`}>
                  {action.icon}
                </div>
                <h4 className="text-sm font-bold text-stone-200 group-hover:text-white transition-colors flex items-center gap-2">
                  {action.label}
                  <ArrowRight className="w-3.5 h-3.5 text-stone-500 group-hover:text-amber-400 group-hover:translate-x-0.5 transition-all" />
                </h4>
                <p className="text-[11px] text-stone-400 mt-1">{action.description}</p>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Tomorrow's Pre-Orders */}
      {summary && summary.tomorrowPreOrders > 0 && (
        <div className="bg-gradient-to-r from-amber-950/30 to-stone-900/80 border border-amber-500/20 rounded-2xl p-5 shadow-xl relative overflow-hidden">
          <div className="absolute -right-6 -top-6 w-24 h-24 bg-amber-500/10 rounded-full blur-2xl pointer-events-none"></div>
          <div className="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center">
                <CalendarClock className="w-5 h-5 text-amber-400" />
              </div>
              <div>
                <p className="text-sm font-bold text-stone-200">Tomorrow's Pre-Orders</p>
                <p className="text-xs text-stone-400">
                  <span className="font-bold text-amber-400 font-mono">{summary.tomorrowPreOrders}</span> orders expected
                  {summary.tomorrowExpectedRevenue > 0 && (
                    <> — <span className="font-bold text-amber-400 font-mono">₹{summary.tomorrowExpectedRevenue.toLocaleString()}</span> revenue</>
                  )}
                </p>
              </div>
            </div>
            {summary.tomorrowIngredientShortfalls > 0 && (
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-amber-500/10 border border-amber-500/30">
                <AlertTriangle className="w-3.5 h-3.5 text-amber-400" />
                <span className="text-[10px] text-amber-300 font-medium">
                  {summary.tomorrowIngredientShortfalls} shortfall{summary.tomorrowIngredientShortfalls !== 1 ? 's' : ''} — restock before morning prep
                </span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

// ─── Sub-components ──────────────────────────────────────────────

function StatCard({
  label,
  value,
  icon,
  gradient,
  pulse,
}: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  gradient: string;
  pulse?: boolean;
}) {
  return (
    <div
      className={`relative overflow-hidden rounded-2xl p-4 bg-gradient-to-br ${gradient} shadow-xl transition-all hover:scale-[1.02] hover:shadow-2xl`}
    >
      <div className="absolute -right-4 -top-4 w-20 h-20 bg-white/10 rounded-full blur-xl pointer-events-none"></div>
      <div className="relative z-10">
        <div className="flex items-center justify-between mb-2">
          <div className="w-9 h-9 rounded-xl bg-white/15 backdrop-blur-sm flex items-center justify-center">
            {icon}
          </div>
          {pulse && <span className="w-2 h-2 rounded-full bg-white animate-pulse"></span>}
        </div>
        <p className="text-2xl md:text-3xl font-bold text-white font-mono tracking-tight">{value}</p>
        <p className="text-[11px] text-white/70 mt-1 font-medium">{label}</p>
      </div>
    </div>
  );
}

function MiniStat({
  label,
  value,
  color,
  icon,
}: {
  label: string;
  value: number;
  color: string;
  icon: React.ReactNode;
}) {
  const colorMap: Record<string, { text: string; bg: string; border: string }> = {
    amber: { text: 'text-amber-400', bg: 'bg-amber-500/10', border: 'border-amber-500/20' },
    emerald: { text: 'text-emerald-400', bg: 'bg-emerald-500/10', border: 'border-emerald-500/20' },
    violet: { text: 'text-violet-400', bg: 'bg-violet-500/10', border: 'border-violet-500/20' },
    sky: { text: 'text-sky-400', bg: 'bg-sky-500/10', border: 'border-sky-500/20' },
  };
  const c = colorMap[color] || colorMap.violet;

  return (
    <div className={`bg-stone-900/80 backdrop-blur-md border border-stone-800 rounded-2xl p-4 transition-all hover:scale-[1.02] hover:shadow-xl`}>
      <div className="flex items-center gap-2 mb-2">
        <div className={`w-8 h-8 rounded-lg ${c.bg} border ${c.border} flex items-center justify-center`}>
          <span className={c.text}>{icon}</span>
        </div>
        <span className="text-[11px] text-stone-400 font-medium">{label}</span>
      </div>
      <p className="text-xl font-bold text-white font-mono">{value}</p>
    </div>
  );
}
