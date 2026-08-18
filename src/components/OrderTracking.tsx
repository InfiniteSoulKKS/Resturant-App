import React, { useEffect, useState } from 'react';
import {
  Clock,
  PackageCheck,
  ChefHat,
  CheckCircle2,
  ShoppingBag,
  Utensils,
  X,
  AlertCircle,
  MapPin,
} from 'lucide-react';
import { Order } from '../types';
import { getMyOrders } from '../lib/apiClient';
import { getToken } from '../lib/tokenManager';

const STATUS_FLOW: { key: string; label: string; icon: React.ReactNode }[] = [
  { key: 'NEW', label: 'Order Placed', icon: <ShoppingBag className="w-4 h-4" /> },
  { key: 'PREPARING', label: 'Being Prepared', icon: <ChefHat className="w-4 h-4" /> },
  { key: 'PACKED_READY', label: 'Packed & Ready', icon: <PackageCheck className="w-4 h-4" /> },
  { key: 'COMPLETED', label: 'Completed', icon: <CheckCircle2 className="w-4 h-4" /> },
];

interface OrderTrackingProps {
  /** Registers a refresh callback that the app invokes on realtime order events. */
  liveUpdate: (handler: () => void) => void;
}

export const OrderTracking: React.FC<OrderTrackingProps> = ({ liveUpdate }) => {
  const [orders, setOrders] = useState<Order[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const loadOrders = () => {
    if (!getToken()) return;
    getMyOrders()
      .then((data) => {
        setOrders(data);
        setIsLoading(false);
      })
      .catch(() => setIsLoading(false));
  };

  useEffect(() => {
    loadOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Register live update handler — the app calls it whenever a realtime order
  // event (status change / ready / new order) arrives for this user, so the
  // tracked list stays in sync without a manual reload.
  useEffect(() => {
    liveUpdate(loadOrders);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!getToken()) {
    return (
      <div className="pt-20 text-center py-20 text-stone-500 text-sm">
        Please sign in to view your orders.
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="pt-20 text-center py-20 text-stone-500 text-sm">Loading your orders...</div>
    );
  }

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      <div className="mb-8">
        <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
          <ShoppingBag className="w-8 h-8 text-amber-400" />
          <span>My Orders</span>
        </h2>
        <p className="text-xs text-stone-400 mt-1">
          Track your orders in real-time. Status updates & notifications appear live.
        </p>
      </div>

      {orders.length === 0 && (
        <div className="text-center py-20 bg-stone-900/60 rounded-3xl border border-stone-800">
          <ShoppingBag className="w-10 h-10 text-stone-600 mx-auto mb-2" />
          <p className="text-sm text-stone-300">No orders yet. Browse the menu to place your first order!</p>
        </div>
      )}

      <div className="grid grid-cols-1 gap-5">
        {orders.map((order) => {
          const statusIdx = STATUS_FLOW.findIndex((s) => s.key === order.orderStatus);
          const isDeclined = order.orderStatus === 'DECLINED';
          const isCompleted = order.orderStatus === 'COMPLETED';

          return (
            <div
              key={order.id}
              className={`bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border shadow-xl transition-all ${
                isDeclined ? 'border-rose-800/50' : 'border-stone-800'
              }`}
            >
              {isDeclined && (
                <div className="flex items-center gap-2 mb-3 p-2.5 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs font-semibold">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  This order was declined.
                </div>
              )}

              <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-3 mb-4">
                <div>
                  <h3 className="text-base font-bold font-serif text-stone-100 flex items-center gap-2">
                    {order.orderNumber}
                    <span className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded-lg ${
                      order.paymentStatus === 'PAID'
                        ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                        : 'bg-amber-500/10 text-amber-400 border border-amber-500/30'
                    }`}>
                      {order.paymentStatus === 'PAID' ? 'Paid' : 'Pending'}
                    </span>
                  </h3>
                  <p className="text-[11px] text-stone-400 mt-1 flex items-center gap-2">
                    <span className="flex items-center gap-1"><MapPin className="w-3 h-3" />{order.orderType === 'DINE_IN' ? `Table ${order.tableNumber}` : 'Pickup'}</span>
                    <span className="flex items-center gap-1"><Clock className="w-3 h-3" />{order.pickupTime || order.timeSlot}</span>
                  </p>
                </div>
                <span className="text-lg font-mono font-bold text-amber-400">₹{order.totalAmount}</span>
              </div>

              {/* Items */}
              <div className="mb-4 p-3 bg-stone-950 rounded-xl border border-stone-800">
                <ul className="text-xs text-stone-300 divide-y divide-stone-800/60">
                  {order.items.map((item, idx) => (
                    <li key={idx} className="py-1.5 flex justify-between">
                      <span className="font-medium">{item.quantity}x {item.title}</span>
                      <span className="font-mono text-stone-400">₹{item.price * item.quantity}</span>
                    </li>
                  ))}
                </ul>
              </div>

              {/* Status Timeline */}
              {!isDeclined && (
                <div className="flex items-center gap-1">
                  {STATUS_FLOW.map((s, i) => {
                    const isActive = i <= statusIdx;
                    const isCurrent = i === statusIdx && !isCompleted;
                    return (
                      <React.Fragment key={s.key}>
                        <div className={`flex flex-col items-center gap-1 group flex-1 min-w-0`}>
                          <div className={`w-8 h-8 rounded-xl flex items-center justify-center transition-all ${
                            isActive
                              ? isCurrent
                                ? 'bg-amber-500 text-stone-950 shadow-lg shadow-amber-500/30 animate-pulse'
                                : 'bg-emerald-500 text-white'
                              : 'bg-stone-800 text-stone-500'
                          }`}>
                            {s.icon}
                          </div>
                          <span className={`text-[9px] font-mono font-bold text-center w-full truncate ${
                            isActive ? 'text-stone-300' : 'text-stone-600'
                          }`}>
                            {s.label}
                          </span>
                        </div>
                        {i < STATUS_FLOW.length - 1 && (
                          <div className={`h-0.5 flex-1 mt-[-20px] ${
                            i < statusIdx ? 'bg-emerald-500/50' : 'bg-stone-800'
                          }`} />
                        )}
                      </React.Fragment>
                    );
                  })}
                </div>
              )}

              {isCompleted && (
                <div className="mt-4 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-bold flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4" />
                  Enjoy your meal! 🎉
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};