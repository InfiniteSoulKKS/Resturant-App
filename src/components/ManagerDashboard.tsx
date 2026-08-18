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

  const tabs: { key: DashboardTab; label: string; icon: React.ReactNode }[] = [
    { key: 'summary', label: 'Today', icon: <LayoutDashboard className="w-4 h-4" /> },
    { key: 'exceptions', label: 'Exceptions', icon: <AlertTriangle className="w-4 h-4" /> },
    { key: 'shopping', label: 'Shopping', icon: <ShoppingCart className="w-4 h-4" /> },
    { key: 'cash', label: 'Cash', icon: <DollarSign className="w-4 h-4" /> },
    { key: 'payment', label: 'Payments', icon: <CreditCard className="w-4 h-4" /> },
  ];

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 text-violet-400 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <LayoutDashboard className="w-5 h-5 text-violet-400" />
          <h2 className="text-lg font-bold text-white">Manager Dashboard</h2>
        </div>
        <button onClick={loadAll} className="text-xs text-slate-400 hover:text-white flex items-center gap-1">
          <RefreshCw className="w-3 h-3" /> Refresh
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-slate-800/50 rounded-lg p-1">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setActiveTab(t.key)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition ${
              activeTab === t.key
                ? 'bg-violet-600 text-white'
                : 'text-slate-400 hover:text-white hover:bg-slate-700/50'
            }`}
          >
            {t.icon} {t.label}
          </button>
        ))}
      </div>

      {/* Summary Tab */}
      {activeTab === 'summary' && summary && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <StatCard label="Total Orders" value={summary.totalOrders} icon={<Package className="w-4 h-4" />} color="violet" />
            <StatCard label="Revenue" value={`₹${summary.revenue.toLocaleString()}`} icon={<TrendingUp className="w-4 h-4" />} color="green" />
            <StatCard label="Preparing" value={summary.preparing} icon={<ChefHat className="w-4 h-4" />} color="amber" />
            <StatCard label="Ready" value={summary.ready} icon={<Package className="w-4 h-4" />} color="emerald" />
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <StatCard label="Pending" value={summary.pending} icon={<Clock className="w-4 h-4" />} color="blue" />
            <StatCard label="Completed" value={summary.completed} icon={<Package className="w-4 h-4" />} color="green" />
            <StatCard label="Delayed" value={summary.delayed} icon={<AlertTriangle className="w-4 h-4" />} color="red" alert={summary.delayed > 0} />
            <StatCard label="Cash Pending" value={summary.cashPaymentsPending} icon={<DollarSign className="w-4 h-4" />} color="amber" alert={summary.cashPaymentsPending > 0} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <StatCard label="Ingredient Shortages" value={summary.ingredientShortages} icon={<Package className="w-4 h-4" />} color="red" alert={summary.ingredientShortages > 0} />
            <StatCard label="Sold-Out Dishes" value={summary.soldOutDishes} icon={<AlertTriangle className="w-4 h-4" />} color="amber" alert={summary.soldOutDishes > 0} />
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
              <p className="text-xs text-slate-400 mb-1">Tomorrow's Pre-Orders</p>
              <p className="text-2xl font-bold text-white">{summary.tomorrowPreOrders}</p>
              <p className="text-xs text-slate-400 mt-1">
                Expected: ₹{summary.tomorrowExpectedRevenue.toLocaleString()}
              </p>
              {summary.tomorrowIngredientShortfalls > 0 && (
                <p className="text-xs text-amber-400 mt-1">
                  {summary.tomorrowIngredientShortfalls} ingredient shortfalls
                </p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Exceptions Tab */}
      {activeTab === 'exceptions' && exceptions && (
        <div className="space-y-3">
          <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
            <ExceptionCard label="Payment Failures" count={exceptions.paymentFailures} color="red" />
            <ExceptionCard label="Delayed Orders" count={exceptions.delayedOrders} color="amber" />
            <ExceptionCard label="Ingredient Shortages" count={exceptions.ingredientShortages} color="red" />
            <ExceptionCard label="Cash Pending" count={exceptions.cashPaymentsPending} color="amber" />
            <ExceptionCard label="Refunds Pending" count={exceptions.refundsPending} color="orange" />
            <ExceptionCard label="Sold-Out Dishes" count={exceptions.soldOutDishes} color="slate" />
          </div>

          {exceptions.delayedOrderDetails.length > 0 && (
            <div className="bg-slate-800/50 border border-amber-500/30 rounded-xl p-4">
              <h3 className="text-sm font-semibold text-amber-400 mb-3 flex items-center gap-2">
                <Clock className="w-4 h-4" /> Delayed Orders
              </h3>
              <div className="space-y-2">
                {exceptions.delayedOrderDetails.map((d: any) => (
                  <div key={d.orderId} className="flex items-center justify-between text-xs">
                    <span className="text-white">{d.orderNumber} — {d.customerName}</span>
                    <span className="text-amber-400 font-medium">+{d.delayMinutes} min late</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* Shopping List Tab */}
      {activeTab === 'shopping' && (
        <div className="space-y-3">
          <div className="flex items-center gap-2">
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => loadDateData(e.target.value)}
              className="bg-slate-700 border border-slate-600 rounded-lg px-3 py-1.5 text-sm text-white"
            />
          </div>

          {shoppingList.length === 0 ? (
            <div className="text-center py-8 text-slate-400 text-sm">
              No ingredient shortfalls — all stocked!
            </div>
          ) : (
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-700/50">
                    <th className="text-left px-4 py-2 text-slate-400 font-medium">Ingredient</th>
                    <th className="text-right px-4 py-2 text-slate-400 font-medium">Required</th>
                    <th className="text-right px-4 py-2 text-slate-400 font-medium">In Stock</th>
                    <th className="text-right px-4 py-2 text-slate-400 font-medium">Shortfall</th>
                  </tr>
                </thead>
                <tbody>
                  {shoppingList.map((item, i) => (
                    <tr key={i} className="border-b border-slate-700/30">
                      <td className="px-4 py-2 text-white">{item.name}</td>
                      <td className="px-4 py-2 text-right text-slate-300">{item.requiredQuantity} {item.unit}</td>
                      <td className="px-4 py-2 text-right text-slate-300">{item.currentStock} {item.unit}</td>
                      <td className="px-4 py-2 text-right text-red-400 font-medium">{item.shortfall} {item.unit}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Cash Reconciliation Tab */}
      {activeTab === 'cash' && cashRecon && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => loadDateData(e.target.value)}
              className="bg-slate-700 border border-slate-600 rounded-lg px-3 py-1.5 text-sm text-white"
            />
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <StatCard label="Total Cash Orders" value={cashRecon.totalCashOrders} icon={<Receipt className="w-4 h-4" />} color="violet" />
            <StatCard label="Expected Cash" value={`₹${cashRecon.expectedCash.toLocaleString()}`} icon={<DollarSign className="w-4 h-4" />} color="green" />
            <StatCard label="Paid" value={cashRecon.paidOrders} icon={<Package className="w-4 h-4" />} color="green" />
            <StatCard label="Pending" value={cashRecon.pendingOrders} icon={<Clock className="w-4 h-4" />} color="amber" alert={cashRecon.pendingOrders > 0} />
          </div>

          {cashRecon.pendingAmount > 0 && (
            <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4">
              <p className="text-sm text-amber-400">
                ₹{cashRecon.pendingAmount.toLocaleString()} in cash payments still pending collection
              </p>
            </div>
          )}
        </div>
      )}

      {/* Payment Reconciliation Tab */}
      {activeTab === 'payment' && paymentRecon && (
        <div className="space-y-4">
          <div className="flex items-center gap-2">
            <input
              type="date"
              value={selectedDate}
              onChange={(e) => loadDateData(e.target.value)}
              className="bg-slate-700 border border-slate-600 rounded-lg px-3 py-1.5 text-sm text-white"
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
              <p className="text-xs text-slate-400">Gross</p>
              <p className="text-2xl font-bold text-green-400">₹{paymentRecon.gross.toLocaleString()}</p>
            </div>
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
              <p className="text-xs text-slate-400">Refunds</p>
              <p className="text-2xl font-bold text-red-400">₹{paymentRecon.refunds.toLocaleString()}</p>
            </div>
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
              <p className="text-xs text-slate-400">Net</p>
              <p className="text-2xl font-bold text-white">₹{paymentRecon.net.toLocaleString()}</p>
            </div>
          </div>

          {Object.keys(paymentRecon.byMethod).length > 0 && (
            <div className="bg-slate-800/50 border border-slate-700/50 rounded-xl p-4">
              <h3 className="text-sm font-semibold text-white mb-3">By Payment Method</h3>
              <div className="space-y-2">
                {Object.entries(paymentRecon.byMethod).map(([method, amount]) => (
                  <div key={method} className="flex items-center justify-between text-sm">
                    <span className="text-slate-300">{method}</span>
                    <div className="flex items-center gap-3">
                      <span className="text-white font-medium">₹{(amount as number).toLocaleString()}</span>
                      <span className="text-slate-400 text-xs">({paymentRecon.countByMethod[method] || 0} orders)</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="grid grid-cols-3 gap-3">
            <ExceptionCard label="Pending Payments" count={paymentRecon.pendingPayments} color="amber" />
            <ExceptionCard label="Failed Payments" count={paymentRecon.failedPayments} color="red" />
            <ExceptionCard label="Cash Pending" count={paymentRecon.cashPending} color="amber" />
          </div>
        </div>
      )}
    </div>
  );
};

// ─── Sub-components ──────────────────────────────────────────────

function StatCard({ label, value, icon, color, alert }: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  color: string;
  alert?: boolean;
}) {
  const colorMap: Record<string, string> = {
    violet: 'text-violet-400',
    green: 'text-green-400',
    amber: 'text-amber-400',
    blue: 'text-blue-400',
    red: 'text-red-400',
    emerald: 'text-emerald-400',
  };
  return (
    <div className={`bg-slate-800/50 border rounded-xl p-3 ${alert ? 'border-amber-500/50' : 'border-slate-700/50'}`}>
      <div className="flex items-center gap-1.5 mb-1">
        <span className={colorMap[color] || 'text-slate-400'}>{icon}</span>
        <span className="text-xs text-slate-400">{label}</span>
      </div>
      <p className="text-xl font-bold text-white">{value}</p>
    </div>
  );
}

function ExceptionCard({ label, count, color }: { label: string; count: number; color: string }) {
  const bgColorMap: Record<string, string> = {
    red: 'bg-red-500/10 border-red-500/30',
    amber: 'bg-amber-500/10 border-amber-500/30',
    orange: 'bg-orange-500/10 border-orange-500/30',
    slate: 'bg-slate-700/30 border-slate-600/30',
  };
  const textColorMap: Record<string, string> = {
    red: 'text-red-400',
    amber: 'text-amber-400',
    orange: 'text-orange-400',
    slate: 'text-slate-400',
  };
  return (
    <div className={`${bgColorMap[color] || bgColorMap.slate} border rounded-xl p-3`}>
      <p className="text-xs text-slate-400">{label}</p>
      <p className={`text-2xl font-bold ${textColorMap[color] || textColorMap.slate}`}>{count}</p>
    </div>
  );
}
