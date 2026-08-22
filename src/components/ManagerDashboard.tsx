import React, { useState, useEffect } from 'react';
import {
  LayoutDashboard,
  AlertTriangle,
  DollarSign,
  ShoppingCart,
  Clock,
  Package,
  TrendingUp,
  TrendingDown,
  RefreshCw,
  Download,
  ChefHat,
  Receipt,
  CreditCard,
  Zap,
  Target,
  BarChart3,
  Activity,
} from 'lucide-react';
import {
  getDashboardSummary,
  getDashboardExceptions,
  getShoppingList,
  getCashReconciliation,
  getPaymentReconciliation,
  type DashboardSummary,
  type DashboardExceptions,
  type ShoppingListItem,
  type CashReconciliation,
  type PaymentReconciliation,
} from '../lib/apiClient';

interface ManagerDashboardProps {
  userRole: string | null;
  restaurantId: string | null;
}

type DashboardTab = 'summary' | 'exceptions' | 'shopping' | 'cash' | 'payment';

export const ManagerDashboard: React.FC<ManagerDashboardProps> = ({ userRole, restaurantId }) => {
  const [activeTab, setActiveTab] = useState<DashboardTab>('summary');
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [exceptions, setExceptions] = useState<DashboardExceptions | null>(null);
  const [shoppingList, setShoppingList] = useState<ShoppingListItem[]>([]);
  const [cashRecon, setCashRecon] = useState<CashReconciliation | null>(null);
  const [paymentRecon, setPaymentRecon] = useState<PaymentReconciliation | null>(null);
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);

  const loadAll = async () => {
    setLoading(true);
    try {
      const [s, e, sl, cr, pr] = await Promise.all([
        getDashboardSummary(restaurantId || undefined),
        getDashboardExceptions(restaurantId || undefined),
        getShoppingList(restaurantId || undefined),
        getCashReconciliation(restaurantId || undefined),
        getPaymentReconciliation(restaurantId || undefined),
      ]);
      setSummary(s);
      setExceptions(e);
      setShoppingList(sl);
      setCashRecon(cr);
      setPaymentRecon(pr);
    } catch (err) {
      console.error('Failed to load dashboard:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadAll(); }, [restaurantId]);

  const loadDateData = async (date: string) => {
    setSelectedDate(date);
    try {
      const [sl, cr, pr] = await Promise.all([
        getShoppingList(restaurantId || undefined, date),
        getCashReconciliation(restaurantId || undefined, date),
        getPaymentReconciliation(restaurantId || undefined, date),
      ]);
      setShoppingList(sl);
      setCashRecon(cr);
      setPaymentRecon(pr);
    } catch (err) {
      console.error('Failed to load date data:', err);
    }
  };

  const tabs: { key: DashboardTab; label: string; icon: React.ReactNode; color: string }[] = [
    { key: 'summary', label: 'Overview', icon: <LayoutDashboard className="w-4 h-4" />, color: 'violet' },
    { key: 'exceptions', label: 'Alerts', icon: <AlertTriangle className="w-4 h-4" />, color: 'red' },
    { key: 'shopping', label: 'Shopping', icon: <ShoppingCart className="w-4 h-4" />, color: 'emerald' },
    { key: 'cash', label: 'Cash', icon: <DollarSign className="w-4 h-4" />, color: 'amber' },
    { key: 'payment', label: 'Payments', icon: <CreditCard className="w-4 h-4" />, color: 'blue' },
  ];

  if (loading) {
    return (
      <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
        <div className="flex flex-col items-center justify-center h-64 gap-4">
          <div className="relative">
            <div className="w-12 h-12 rounded-2xl bg-gradient-to-br from-violet-500/20 to-amber-500/20 border border-violet-500/30 flex items-center justify-center">
              <RefreshCw className="w-6 h-6 text-violet-400 animate-spin" />
            </div>
          </div>
          <p className="text-sm text-stone-400 font-medium">Loading dashboard data...</p>
        </div>
      </div>
    );
  }

  // Calculate completion rate for progress bar
  const completionRate = summary ? (summary.totalOrders > 0 ? Math.round((summary.completed / summary.totalOrders) * 100) : 0) : 0;

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      {/* Hero Header */}
      <div className="relative rounded-3xl overflow-hidden bg-gradient-to-r from-violet-950/80 via-stone-900/90 to-amber-950/40 border border-stone-800 p-6 md:p-8 mb-8 shadow-2xl">
        <div className="absolute -right-10 -top-10 w-60 h-60 bg-violet-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="absolute -left-10 -bottom-10 w-40 h-40 bg-amber-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-violet-500/10 border border-violet-500/30 text-violet-400 text-xs font-semibold mb-3">
              <Activity className="w-3.5 h-3.5" />
              <span>Live Dashboard</span>
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            </div>
            <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
              <BarChart3 className="w-8 h-8 text-violet-400" />
              <span>Manager Dashboard</span>
            </h2>
            <p className="text-xs text-stone-400 mt-1">
              Real-time overview of your restaurant operations, orders, and revenue.
            </p>
          </div>
          <div className="flex items-center gap-3">
            {/* Completion Rate Ring */}
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
                    stroke="rgb(139 92 246)"
                    strokeWidth="3"
                    strokeDasharray={`${completionRate}, 100`}
                    className="transition-all duration-1000"
                  />
                </svg>
                <div className="absolute inset-0 flex items-center justify-center">
                  <span className="text-xs font-bold text-violet-400">{completionRate}%</span>
                </div>
              </div>
              <div>
                <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold">Completion</p>
                <p className="text-sm font-bold text-stone-200">{summary?.completed || 0} / {summary?.totalOrders || 0}</p>
              </div>
            </div>
            <button
              onClick={loadAll}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-stone-900/80 border border-stone-800 hover:border-violet-500/40 text-stone-300 hover:text-violet-400 text-xs font-medium transition-all cursor-pointer"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Refresh
            </button>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-stone-900/60 rounded-2xl p-1.5 mb-6 border border-stone-800/80 overflow-x-auto">
        {tabs.map((t) => {
          const isActive = activeTab === t.key;
          const colorMap: Record<string, string> = {
            violet: isActive ? 'bg-violet-600 text-white' : '',
            red: isActive ? 'bg-red-600 text-white' : '',
            emerald: isActive ? 'bg-emerald-600 text-white' : '',
            amber: isActive ? 'bg-amber-600 text-white' : '',
            blue: isActive ? 'bg-blue-600 text-white' : '',
          };
          return (
            <button
              key={t.key}
              onClick={() => setActiveTab(t.key)}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
                isActive
                  ? `${colorMap[t.color]} shadow-lg`
                  : 'text-stone-400 hover:text-white hover:bg-stone-800/50'
              }`}
            >
              {t.icon} {t.label}
            </button>
          );
        })}
      </div>

      {/* Summary Tab */}
      {activeTab === 'summary' && summary && (
        <div className="space-y-6">
          {/* Primary Stats Row */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <GradientStatCard
              label="Total Orders"
              value={summary.totalOrders}
              icon={<Package className="w-5 h-5" />}
              gradient="from-violet-600 to-purple-700"
              trend={summary.totalOrders > 10 ? 'up' : undefined}
            />
            <GradientStatCard
              label="Revenue"
              value={`₹${summary.revenue.toLocaleString()}`}
              icon={<TrendingUp className="w-5 h-5" />}
              gradient="from-emerald-600 to-green-700"
              trend="up"
            />
            <GradientStatCard
              label="Preparing"
              value={summary.preparing}
              icon={<ChefHat className="w-5 h-5" />}
              gradient="from-amber-500 to-orange-600"
              pulse={summary.preparing > 0}
            />
            <GradientStatCard
              label="Ready"
              value={summary.ready}
              icon={<Zap className="w-5 h-5" />}
              gradient="from-cyan-500 to-blue-600"
            />
          </div>

          {/* Secondary Stats Row */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <GlassStatCard
              label="Pending"
              value={summary.pending}
              icon={<Clock className="w-4 h-4" />}
              color="blue"
              alert={summary.pending > 5}
            />
            <GlassStatCard
              label="Completed"
              value={summary.completed}
              icon={<Target className="w-4 h-4" />}
              color="emerald"
            />
            <GlassStatCard
              label="Delayed"
              value={summary.delayed}
              icon={<AlertTriangle className="w-4 h-4" />}
              color="red"
              alert={summary.delayed > 0}
            />
            <GlassStatCard
              label="Cash Pending"
              value={summary.cashPaymentsPending}
              icon={<DollarSign className="w-4 h-4" />}
              color="amber"
              alert={summary.cashPaymentsPending > 0}
            />
          </div>

          {/* Progress + Tomorrow Summary */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Order Pipeline Progress */}
            <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
              <h3 className="text-sm font-bold text-stone-200 mb-4 flex items-center gap-2">
                <Activity className="w-4 h-4 text-violet-400" />
                Order Pipeline
              </h3>
              <div className="space-y-3">
                <PipelineRow label="New" count={summary.pending} total={summary.totalOrders} color="bg-blue-500" />
                <PipelineRow label="Preparing" count={summary.preparing} total={summary.totalOrders} color="bg-amber-500" />
                <PipelineRow label="Ready" count={summary.ready} total={summary.totalOrders} color="bg-cyan-500" />
                <PipelineRow label="Completed" count={summary.completed} total={summary.totalOrders} color="bg-emerald-500" />
              </div>
            </div>

            {/* Tomorrow's Pre-Orders */}
            <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl relative overflow-hidden">
              <div className="absolute -right-6 -top-6 w-24 h-24 bg-amber-500/10 rounded-full blur-2xl pointer-events-none"></div>
              <h3 className="text-sm font-bold text-stone-200 mb-4 flex items-center gap-2 relative z-10">
                <TrendingUp className="w-4 h-4 text-amber-400" />
                Tomorrow's Pre-Orders
              </h3>
              <div className="relative z-10 space-y-4">
                <div className="flex items-baseline gap-2">
                  <span className="text-3xl font-bold text-stone-100 font-mono">{summary.tomorrowPreOrders}</span>
                  <span className="text-xs text-stone-400">orders expected</span>
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <span className="text-stone-400">Expected Revenue:</span>
                  <span className="font-bold text-amber-400 font-mono">₹{summary.tomorrowExpectedRevenue.toLocaleString()}</span>
                </div>
                {summary.tomorrowIngredientShortfalls > 0 && (
                  <div className="flex items-center gap-2 p-2.5 rounded-xl bg-amber-500/10 border border-amber-500/30">
                    <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
                    <span className="text-xs text-amber-300 font-medium">
                      {summary.tomorrowIngredientShortfalls} ingredient shortfall{summary.tomorrowIngredientShortfalls !== 1 ? 's' : ''} — restock before morning prep
                    </span>
                  </div>
                )}
                {summary.tomorrowPreOrders === 0 && (
                  <p className="text-xs text-stone-500 italic">No pre-orders scheduled for tomorrow yet.</p>
                )}
              </div>
            </div>

            {/* Ingredient Shortages & Sold-Out */}
            <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
              <h3 className="text-sm font-bold text-stone-200 mb-4 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-red-400" />
                Stock Alerts
              </h3>
              <div className="space-y-3">
                <div className={`flex items-center justify-between p-3 rounded-xl border ${summary.ingredientShortages > 0 ? 'bg-red-500/10 border-red-500/30' : 'bg-emerald-500/5 border-emerald-500/20'}`}>
                  <div className="flex items-center gap-2">
                    <Package className={`w-4 h-4 ${summary.ingredientShortages > 0 ? 'text-red-400' : 'text-emerald-400'}`} />
                    <span className="text-xs font-medium text-stone-300">Ingredient Shortages</span>
                  </div>
                  <span className={`text-lg font-bold font-mono ${summary.ingredientShortages > 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                    {summary.ingredientShortages}
                  </span>
                </div>
                <div className={`flex items-center justify-between p-3 rounded-xl border ${summary.soldOutDishes > 0 ? 'bg-amber-500/10 border-amber-500/30' : 'bg-emerald-500/5 border-emerald-500/20'}`}>
                  <div className="flex items-center gap-2">
                    <AlertTriangle className={`w-4 h-4 ${summary.soldOutDishes > 0 ? 'text-amber-400' : 'text-emerald-400'}`} />
                    <span className="text-xs font-medium text-stone-300">Sold-Out Dishes</span>
                  </div>
                  <span className={`text-lg font-bold font-mono ${summary.soldOutDishes > 0 ? 'text-amber-400' : 'text-emerald-400'}`}>
                    {summary.soldOutDishes}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Exceptions Tab */}
      {activeTab === 'exceptions' && exceptions && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            <AlertCard label="Payment Failures" count={exceptions.paymentFailures} color="red" icon={<CreditCard className="w-5 h-5" />} />
            <AlertCard label="Delayed Orders" count={exceptions.delayedOrders} color="amber" icon={<Clock className="w-5 h-5" />} />
            <AlertCard label="Ingredient Shortages" count={exceptions.ingredientShortages} color="red" icon={<Package className="w-5 h-5" />} />
            <AlertCard label="Cash Pending" count={exceptions.cashPaymentsPending} color="amber" icon={<DollarSign className="w-5 h-5" />} />
            <AlertCard label="Refunds Pending" count={exceptions.refundsPending} color="orange" icon={<Receipt className="w-5 h-5" />} />
            <AlertCard label="Sold-Out Dishes" count={exceptions.soldOutDishes} color="slate" icon={<AlertTriangle className="w-5 h-5" />} />
          </div>

          {/* Quick counts row */}
          <div className="grid grid-cols-3 gap-4">
            <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-blue-500/20">
              <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold mb-1">New Orders</p>
              <p className="text-2xl font-bold text-blue-400 font-mono">{exceptions.newOrders}</p>
            </div>
            <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-amber-500/20">
              <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold mb-1">Preparing</p>
              <p className="text-2xl font-bold text-amber-400 font-mono">{exceptions.preparingOrders}</p>
            </div>
            <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 border border-emerald-500/20">
              <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold mb-1">Ready for Pickup</p>
              <p className="text-2xl font-bold text-emerald-400 font-mono">{exceptions.readyOrders}</p>
            </div>
          </div>

          {exceptions.delayedOrderDetails.length > 0 && (
            <div className="bg-gradient-to-r from-amber-950/30 to-stone-900/80 border border-amber-500/30 rounded-2xl p-5 shadow-xl">
              <h3 className="text-sm font-bold text-amber-400 mb-4 flex items-center gap-2">
                <Clock className="w-4 h-4" /> Delayed Orders — Action Required
              </h3>
              <div className="space-y-2">
                {exceptions.delayedOrderDetails.map((d: any) => (
                  <div key={d.orderId} className="flex items-center justify-between text-xs p-3 bg-stone-900/60 rounded-xl border border-stone-800/50">
                    <div className="flex items-center gap-3">
                      <span className="font-mono font-bold text-white">{d.orderNumber}</span>
                      <span className="text-stone-400">—</span>
                      <span className="text-stone-300">{d.customerName}</span>
                    </div>
                    <span className="text-amber-400 font-bold font-mono">+{d.delayMinutes} min late</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Shopping List Tab */}
      {activeTab === 'shopping' && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 bg-stone-900/80 rounded-xl border border-stone-800 px-3 py-2">
              <label className="text-xs text-stone-400 font-medium">Date:</label>
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => loadDateData(e.target.value)}
                className="bg-transparent border-none text-sm text-white focus:outline-none cursor-pointer"
              />
            </div>
          </div>

          {shoppingList.length === 0 ? (
            <div className="text-center py-16 bg-stone-900/60 rounded-3xl border border-stone-800">
              <div className="w-16 h-16 rounded-2xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center mx-auto mb-4">
                <Package className="w-8 h-8 text-emerald-400" />
              </div>
              <p className="text-sm font-bold text-stone-300">All Stocked Up!</p>
              <p className="text-xs text-stone-500 mt-1">No ingredient shortfalls detected for this date.</p>
            </div>
          ) : (
            <div className="bg-stone-900/80 backdrop-blur-md border border-stone-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="p-4 border-b border-stone-800">
                <h3 className="text-sm font-bold text-stone-200 flex items-center gap-2">
                  <ShoppingCart className="w-4 h-4 text-emerald-400" />
                  Shopping List — {shoppingList.length} item{shoppingList.length !== 1 ? 's' : ''}
                </h3>
              </div>
              <div className="divide-y divide-stone-800/50">
                {shoppingList.map((item, i) => (
                  <div key={i} className="flex items-center justify-between px-4 py-3 hover:bg-stone-800/30 transition-colors">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-lg bg-stone-800 flex items-center justify-center">
                        <span className="text-xs font-bold text-stone-400">{i + 1}</span>
                      </div>
                      <div>
                        <p className="text-sm font-medium text-white">{item.name}</p>
                        <p className="text-[10px] text-stone-500">
                          Stock: {item.currentStock} {item.unit}
                        </p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className="text-xs text-stone-400">Need: {item.requiredQuantity} {item.unit}</p>
                      <p className="text-sm font-bold text-red-400 font-mono">-{item.shortfall} {item.unit}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Cash Reconciliation Tab */}
      {activeTab === 'cash' && cashRecon && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 bg-stone-900/80 rounded-xl border border-stone-800 px-3 py-2">
              <label className="text-xs text-stone-400 font-medium">Date:</label>
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => loadDateData(e.target.value)}
                className="bg-transparent border-none text-sm text-white focus:outline-none cursor-pointer"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <GradientStatCard
              label="Total Cash Orders"
              value={cashRecon.totalCashOrders}
              icon={<Receipt className="w-5 h-5" />}
              gradient="from-violet-600 to-purple-700"
            />
            <GradientStatCard
              label="Expected Cash"
              value={`₹${cashRecon.expectedCash.toLocaleString()}`}
              icon={<DollarSign className="w-5 h-5" />}
              gradient="from-emerald-600 to-green-700"
            />
            <GradientStatCard
              label="Collected"
              value={cashRecon.paidOrders}
              icon={<Package className="w-5 h-5" />}
              gradient="from-cyan-500 to-blue-600"
            />
            <GradientStatCard
              label="Pending"
              value={cashRecon.pendingOrders}
              icon={<Clock className="w-5 h-5" />}
              gradient={cashRecon.pendingOrders > 0 ? 'from-amber-500 to-orange-600' : 'from-stone-600 to-stone-700'}
              pulse={cashRecon.pendingOrders > 0}
            />
          </div>

          {cashRecon.pendingAmount > 0 && (
            <div className="bg-gradient-to-r from-amber-950/40 to-stone-900/80 border border-amber-500/30 rounded-2xl p-5 shadow-xl">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center">
                  <DollarSign className="w-5 h-5 text-amber-400" />
                </div>
                <div>
                  <p className="text-xs text-stone-400">Pending Collection</p>
                  <p className="text-xl font-bold text-amber-400 font-mono">₹{cashRecon.pendingAmount.toLocaleString()}</p>
                </div>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Payment Reconciliation Tab */}
      {activeTab === 'payment' && paymentRecon && (
        <div className="space-y-4">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 bg-stone-900/80 rounded-xl border border-stone-800 px-3 py-2">
              <label className="text-xs text-stone-400 font-medium">Date:</label>
              <input
                type="date"
                value={selectedDate}
                onChange={(e) => loadDateData(e.target.value)}
                className="bg-transparent border-none text-sm text-white focus:outline-none cursor-pointer"
              />
            </div>
          </div>

          {/* Revenue Cards */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-gradient-to-br from-emerald-950/40 to-stone-900/80 border border-emerald-500/20 rounded-2xl p-5 shadow-xl">
              <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold mb-1">Gross Revenue</p>
              <p className="text-3xl font-bold text-emerald-400 font-mono">₹{paymentRecon.gross.toLocaleString()}</p>
            </div>
            <div className="bg-gradient-to-br from-red-950/40 to-stone-900/80 border border-red-500/20 rounded-2xl p-5 shadow-xl">
              <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold mb-1">Refunds</p>
              <p className="text-3xl font-bold text-red-400 font-mono">₹{paymentRecon.refunds.toLocaleString()}</p>
            </div>
            <div className="bg-gradient-to-br from-violet-950/40 to-stone-900/80 border border-violet-500/20 rounded-2xl p-5 shadow-xl">
              <p className="text-[10px] text-stone-500 uppercase tracking-wider font-semibold mb-1">Net Revenue</p>
              <p className="text-3xl font-bold text-violet-400 font-mono">₹{paymentRecon.net.toLocaleString()}</p>
            </div>
          </div>

          {Object.keys(paymentRecon.byMethod).length > 0 && (
            <div className="bg-stone-900/80 backdrop-blur-md border border-stone-800 rounded-2xl overflow-hidden shadow-xl">
              <div className="p-4 border-b border-stone-800">
                <h3 className="text-sm font-bold text-stone-200 flex items-center gap-2">
                  <CreditCard className="w-4 h-4 text-blue-400" />
                  Revenue by Payment Method
                </h3>
              </div>
              <div className="divide-y divide-stone-800/50">
                {Object.entries(paymentRecon.byMethod).map(([method, amount]) => {
                  const maxAmount = Math.max(...Object.values(paymentRecon.byMethod) as number[]);
                  const width = maxAmount > 0 ? ((amount as number) / maxAmount) * 100 : 0;
                  return (
                    <div key={method} className="px-4 py-3">
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-white">{method}</span>
                          <span className="text-[10px] text-stone-500">({paymentRecon.countByMethod[method] || 0} orders)</span>
                        </div>
                        <span className="text-sm font-bold text-stone-200 font-mono">₹{(amount as number).toLocaleString()}</span>
                      </div>
                      <div className="w-full h-1.5 bg-stone-800 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-gradient-to-r from-violet-500 to-amber-500 rounded-full transition-all duration-500"
                          style={{ width: `${width}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className="grid grid-cols-3 gap-4">
            <AlertCard label="Pending Payments" count={paymentRecon.pendingPayments} color="amber" icon={<Clock className="w-5 h-5" />} />
            <AlertCard label="Failed Payments" count={paymentRecon.failedPayments} color="red" icon={<AlertTriangle className="w-5 h-5" />} />
            <AlertCard label="Cash Pending" count={paymentRecon.cashPending} color="amber" icon={<DollarSign className="w-5 h-5" />} />
          </div>
        </div>
      )}
    </div>
  );
};

// ─── Sub-components ──────────────────────────────────────────────

function GradientStatCard({ label, value, icon, gradient, trend, pulse }: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  gradient: string;
  trend?: 'up' | 'down';
  pulse?: boolean;
}) {
  return (
    <div className={`relative overflow-hidden rounded-2xl p-4 bg-gradient-to-br ${gradient} shadow-xl transition-all hover:scale-[1.02] hover:shadow-2xl`}>
      {/* Background decoration */}
      <div className="absolute -right-4 -top-4 w-20 h-20 bg-white/10 rounded-full blur-xl pointer-events-none"></div>
      <div className="absolute -left-2 -bottom-2 w-12 h-12 bg-white/5 rounded-full blur-lg pointer-events-none"></div>
      <div className="relative z-10">
        <div className="flex items-center justify-between mb-2">
          <div className="w-9 h-9 rounded-xl bg-white/15 backdrop-blur-sm flex items-center justify-center">
            {icon}
          </div>
          {pulse && (
            <span className="w-2 h-2 rounded-full bg-white animate-pulse"></span>
          )}
        </div>
        <p className="text-2xl md:text-3xl font-bold text-white font-mono tracking-tight">{value}</p>
        <p className="text-[11px] text-white/70 mt-1 font-medium">{label}</p>
      </div>
    </div>
  );
}

function GlassStatCard({ label, value, icon, color, alert }: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  color: string;
  alert?: boolean;
}) {
  const colorMap: Record<string, { text: string; border: string; bg: string }> = {
    violet: { text: 'text-violet-400', border: 'border-violet-500/20', bg: 'bg-violet-500/10' },
    green: { text: 'text-emerald-400', border: 'border-emerald-500/20', bg: 'bg-emerald-500/10' },
    emerald: { text: 'text-emerald-400', border: 'border-emerald-500/20', bg: 'bg-emerald-500/10' },
    amber: { text: 'text-amber-400', border: 'border-amber-500/20', bg: 'bg-amber-500/10' },
    blue: { text: 'text-blue-400', border: 'border-blue-500/20', bg: 'bg-blue-500/10' },
    red: { text: 'text-red-400', border: 'border-red-500/20', bg: 'bg-red-500/10' },
  };
  const c = colorMap[color] || colorMap.violet;
  return (
    <div className={`bg-stone-900/80 backdrop-blur-md border rounded-2xl p-4 transition-all hover:scale-[1.02] hover:shadow-xl ${
      alert ? `border-amber-500/40 shadow-amber-500/10 shadow-lg` : `border-stone-800 shadow-xl`
    }`}>
      <div className="flex items-center gap-2 mb-2">
        <div className={`w-8 h-8 rounded-lg ${c.bg} flex items-center justify-center`}>
          <span className={c.text}>{icon}</span>
        </div>
        <span className="text-[11px] text-stone-400 font-medium">{label}</span>
      </div>
      <p className="text-xl font-bold text-white font-mono">{value}</p>
    </div>
  );
}

function PipelineRow({ label, count, total, color }: {
  label: string;
  count: number;
  total: number;
  color: string;
}) {
  const pct = total > 0 ? (count / total) * 100 : 0;
  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-xs text-stone-300 font-medium">{label}</span>
        <span className="text-xs font-bold text-stone-200 font-mono">{count}</span>
      </div>
      <div className="w-full h-2 bg-stone-800 rounded-full overflow-hidden">
        <div
          className={`h-full ${color} rounded-full transition-all duration-700 ease-out`}
          style={{ width: `${pct}%` }}
        />
      </div>
    </div>
  );
}

function AlertCard({ label, count, color, icon }: { label: string; count: number; color: string; icon: React.ReactNode }) {
  const styleMap: Record<string, { bg: string; border: string; text: string; iconText: string }> = {
    red: { bg: 'bg-red-950/30', border: 'border-red-500/30', text: 'text-red-400', iconText: 'text-red-400' },
    amber: { bg: 'bg-amber-950/30', border: 'border-amber-500/30', text: 'text-amber-400', iconText: 'text-amber-400' },
    orange: { bg: 'bg-orange-950/30', border: 'border-orange-500/30', text: 'text-orange-400', iconText: 'text-orange-400' },
    slate: { bg: 'bg-stone-800/30', border: 'border-stone-600/30', text: 'text-stone-400', iconText: 'text-stone-400' },
  };
  const s = styleMap[color] || styleMap.slate;
  return (
    <div className={`${s.bg} border ${s.border} rounded-2xl p-4 transition-all hover:scale-[1.02] hover:shadow-xl`}>
      <div className="flex items-center justify-between mb-2">
        <span className={s.iconText}>{icon}</span>
        {count > 0 && (
          <span className="w-2 h-2 rounded-full bg-current animate-pulse"></span>
        )}
      </div>
      <p className="text-2xl font-bold font-mono mt-2" style={{ color: count > 0 ? undefined : 'rgb(113 113 122)' }}>
        <span className={count > 0 ? s.text : 'text-stone-500'}>{count}</span>
      </p>
      <p className="text-[11px] text-stone-400 mt-1 font-medium">{label}</p>
    </div>
  );
}
