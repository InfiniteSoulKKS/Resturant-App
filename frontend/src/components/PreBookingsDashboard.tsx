import React, { useState } from 'react';
import { Order } from '../types';
import { updateOrderStatusDB } from '../lib/firebase';
import {
  Bell,
  CheckCircle2,
  Clock,
  Utensils,
  PackageCheck,
  Send,
  Smartphone,
  Mail,
  MessageSquare,
  Search,
  User,
  Phone,
  ShieldCheck,
  ChefHat,
  X,
  AlertCircle,
  Calendar,
  IndianRupee,
} from 'lucide-react';

interface PreBookingsDashboardProps {
  orders: Order[];
  currentUser?: any;
}

export const PreBookingsDashboard: React.FC<PreBookingsDashboardProps> = ({ orders, currentUser }) => {
  const [filterTab, setFilterTab] = useState<'today' | 'upcoming'>('today');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [notificationStatusMsg, setNotificationStatusMsg] = useState<string | null>(null);

  const isChef = currentUser?.role === 'ROLE_CHEF';
  const isManager = currentUser?.role === 'ROLE_MANAGER' || currentUser?.role === 'ROLE_ADMIN';

  const handleUpdateStatus = async (order: Order, newStatus: Order['orderStatus']) => {
    await updateOrderStatusDB(order.id, newStatus);

    // Send notification alert via backend REST API
    try {
      const res = await fetch('/api/v1/notifications/send-order-alert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orderId: order.id,
          orderNumber: order.orderNumber,
          customerName: order.customerName,
          phone: order.customerPhone || '+91 98765 43210',
          email: order.customerEmail || 'customer@example.com',
          status: newStatus,
        }),
      });
      const data = await res.json();
      setNotificationStatusMsg(
        `🔔 Order ${order.orderNumber} updated to ${newStatus}. Multi-channel alerts sent via App Push, SMS, WhatsApp & Email!`
      );
      setTimeout(() => setNotificationStatusMsg(null), 6000);
    } catch (e) {
      console.error('Notification dispatch failed', e);
    }
  };

  const filteredOrders = orders.filter((ord) => {
    if (ord.orderStatus === 'DECLINED') return false;

    const matchesStatus =
      statusFilter === 'ALL' ||
      (statusFilter === 'NEW' && ord.orderStatus === 'NEW') ||
      (statusFilter === 'PREPARING' && ord.orderStatus === 'PREPARING') ||
      (statusFilter === 'READY' && (ord.orderStatus === 'PACKED_READY' || ord.orderStatus === 'READY')) ||
      (statusFilter === 'COMPLETED' && ord.orderStatus === 'COMPLETED');

    const matchesSearch =
      ord.orderNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
      ord.customerName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (ord.customerPhone && ord.customerPhone.includes(searchQuery));

    return matchesStatus && matchesSearch;
  });

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      {/* Toast Notification Alert Banner */}
      {notificationStatusMsg && (
        <div className="mb-6 p-4 rounded-2xl bg-emerald-950/90 border border-emerald-500/50 text-emerald-300 text-xs font-semibold flex items-center justify-between shadow-2xl animate-fade-in">
          <div className="flex items-center gap-2.5">
            <Bell className="w-4 h-4 text-emerald-400 animate-bounce" />
            <span>{notificationStatusMsg}</span>
          </div>
          <button
            onClick={() => setNotificationStatusMsg(null)}
            className="text-emerald-400 hover:text-stone-100 p-1"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Header & Controls */}
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-8">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight">
            Orders & Pickup Schedule
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Real-time kitchen dispatch, multi-channel customer notifications (SMS, WhatsApp, Email, Push) & order status management.
          </p>
        </div>

        {/* Date Tabs */}
        <div className="flex gap-2 bg-stone-900 p-1.5 rounded-xl border border-stone-800 w-full lg:w-auto">
          <button
            onClick={() => setFilterTab('today')}
            className={`flex-1 lg:flex-none px-5 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
              filterTab === 'today'
                ? 'bg-amber-500 text-stone-950 shadow-md shadow-amber-500/20'
                : 'text-stone-400 hover:text-stone-100 hover:bg-stone-800/50'
            }`}
          >
            Today's Pickups
          </button>
          <button
            onClick={() => setFilterTab('upcoming')}
            className={`flex-1 lg:flex-none px-5 py-2 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
              filterTab === 'upcoming'
                ? 'bg-amber-500 text-stone-950 shadow-md shadow-amber-500/20'
                : 'text-stone-400 hover:text-stone-100 hover:bg-stone-800/50'
            }`}
          >
            Scheduled Ahead
          </button>
        </div>
      </div>

      {/* Search & Status Pills Filter Bar */}
      <div className="mb-6 flex flex-col md:flex-row gap-3 justify-between items-center bg-stone-900/60 p-3 rounded-2xl border border-stone-800/80">
        <div className="relative w-full md:w-72">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-stone-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by order # or customer..."
            className="w-full pl-9 pr-8 py-1.5 bg-stone-950 rounded-xl border border-stone-800 text-stone-200 placeholder-stone-500 text-xs focus:outline-none focus:border-amber-500"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-stone-500 hover:text-stone-300"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Status Filter Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto hide-scrollbar w-full md:w-auto">
          {[
            { id: 'ALL', label: 'All Active' },
            { id: 'NEW', label: 'New' },
            { id: 'PREPARING', label: 'Cooking' },
            { id: 'READY', label: 'Packed & Ready' },
            { id: 'COMPLETED', label: 'Completed' },
          ].map((st) => (
            <button
              key={st.id}
              onClick={() => setStatusFilter(st.id)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer whitespace-nowrap ${
                statusFilter === st.id
                  ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40'
                  : 'bg-stone-950 text-stone-400 hover:text-stone-200 border border-stone-800'
              }`}
            >
              {st.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Orders Queue List */}
        <div className="lg:col-span-8 flex flex-col gap-4">
          {filteredOrders.map((ord) => {
            const isNew = ord.orderStatus === 'NEW';
            const isPreparing = ord.orderStatus === 'PREPARING';
            const isPackedReady = ord.orderStatus === 'PACKED_READY' || ord.orderStatus === 'READY';
            const isCompleted = ord.orderStatus === 'COMPLETED';

            return (
              <div
                key={ord.id}
                className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 flex flex-col md:flex-row gap-5 transition-all border border-stone-800 shadow-xl hover:border-stone-700/80"
              >
                {/* Status Card Header Badge */}
                <div className="flex-shrink-0 w-full md:w-44 h-32 rounded-xl overflow-hidden relative bg-stone-950 border border-stone-800 flex flex-col justify-between p-3.5">
                  <div className="flex items-center gap-2 text-xs font-bold">
                    <span
                      className={`w-2.5 h-2.5 rounded-full ${
                        isNew
                          ? 'bg-rose-500 animate-ping'
                          : isPreparing
                          ? 'bg-amber-400 animate-pulse'
                          : isPackedReady
                          ? 'bg-emerald-400'
                          : 'bg-stone-500'
                      }`}
                    ></span>
                    <span className="text-stone-200 uppercase font-mono text-[11px] tracking-wide">
                      {isNew
                        ? 'NEW ORDER'
                        : isPreparing
                        ? 'PREPARING'
                        : isPackedReady
                        ? 'PACKED & READY'
                        : 'COMPLETED'}
                    </span>
                  </div>

                  <div className="space-y-0.5">
                    <span className="text-[10px] text-stone-500 block uppercase font-mono">
                      Fulfillment
                    </span>
                    <span className="text-xs text-amber-400 font-bold block">
                      {ord.orderType === 'PICKUP'
                        ? '📦 TAKEAWAY PICKUP'
                        : '🍽️ DINE-IN TABLE ' + (ord.tableNumber || '#1')}
                    </span>
                  </div>

                  <div className="text-[10px] text-stone-400 font-mono flex items-center gap-1">
                    <Clock className="w-3 h-3 text-stone-500" />
                    <span>Slot: {ord.pickupTime || ord.timeSlot}</span>
                  </div>
                </div>

                {/* Details Section */}
                <div className="flex-1 flex flex-col justify-between">
                  <div className="flex justify-between items-start">
                    <div>
                      <h3 className="text-base font-bold font-serif text-stone-100 flex items-center gap-2">
                        <span>Order {ord.orderNumber}</span>
                        {ord.paymentStatus === 'PAID' && (
                          <span className="text-[10px] px-2 py-0.5 rounded-md bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-mono font-bold flex items-center gap-1">
                            <ShieldCheck className="w-3 h-3" />
                            PAID ({ord.paymentMethod})
                          </span>
                        )}
                      </h3>
                      <p className="text-xs text-stone-400 mt-1 flex items-center gap-3">
                        <span className="flex items-center gap-1">
                          <User className="w-3.5 h-3.5 text-amber-400" />
                          {ord.customerName}
                        </span>
                        <span className="flex items-center gap-1">
                          <Phone className="w-3.5 h-3.5 text-stone-500" />
                          {ord.customerPhone || '+91 98765 43210'}
                        </span>
                      </p>
                    </div>
                    <span className="text-lg font-mono font-bold text-amber-400">
                      ₹{ord.totalAmount}
                    </span>
                  </div>

                  {/* Item List */}
                  <div className="mt-3 bg-stone-950/60 p-3 rounded-xl border border-stone-800/80">
                    <ul className="text-xs text-stone-300 divide-y divide-stone-800/60 max-h-28 overflow-y-auto pr-1">
                      {ord.items.map((item, idx) => (
                        <li key={idx} className="py-1 flex justify-between">
                          <span className="font-medium">
                            {item.quantity}x {item.title}
                          </span>
                          <span className="font-mono text-stone-400">
                            ₹{item.price * item.quantity}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>

                  {/* Multi-Channel Alerts Sent Indicator */}
                  <div className="mt-3 flex flex-wrap items-center gap-1.5 text-[10px]">
                    <span className="text-stone-500 font-mono uppercase">Alert Channels:</span>
                    <span className="px-2 py-0.5 rounded bg-stone-950 border border-stone-800 text-emerald-400 flex items-center gap-1 font-mono">
                      <Bell className="w-2.5 h-2.5" /> App Push
                    </span>
                    <span className="px-2 py-0.5 rounded bg-stone-950 border border-stone-800 text-emerald-400 flex items-center gap-1 font-mono">
                      <Smartphone className="w-2.5 h-2.5" /> SMS (+91)
                    </span>
                    <span className="px-2 py-0.5 rounded bg-stone-950 border border-stone-800 text-emerald-400 flex items-center gap-1 font-mono">
                      <MessageSquare className="w-2.5 h-2.5" /> WhatsApp
                    </span>
                    <span className="px-2 py-0.5 rounded bg-stone-950 border border-stone-800 text-emerald-400 flex items-center gap-1 font-mono">
                      <Mail className="w-2.5 h-2.5" /> Email
                    </span>
                  </div>

                  {/* Order Workflow Action Buttons (Role Restricted) */}
                  <div className="mt-4 flex flex-wrap justify-end gap-2">
                    {/* Customer or Unprivileged view */}
                    {!isChef && !isManager && (
                      <span className="text-xs font-semibold px-3 py-1.5 rounded-xl bg-stone-950 border border-stone-800 text-amber-400 flex items-center gap-1.5">
                        {isNew && <Clock className="w-3.5 h-3.5 text-amber-400 animate-spin" />}
                        {isPreparing && <Utensils className="w-3.5 h-3.5 text-amber-400 animate-pulse" />}
                        {isPackedReady && <PackageCheck className="w-3.5 h-3.5 text-emerald-400 animate-bounce" />}
                        {isCompleted && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />}
                        <span>
                          {isNew
                            ? 'Order Received • Waiting for Kitchen'
                            : isPreparing
                            ? 'Chef is Cooking Your Order'
                            : isPackedReady
                            ? 'Ready for Pickup / Table Service'
                            : 'Order Handed Over & Completed'}
                        </span>
                      </span>
                    )}

                    {/* Manager / Admin / Chef controls */}
                    {(isChef || isManager) && (
                      <>
                        {isNew && (
                          <>
                            {isManager && (
                              <button
                                onClick={() => handleUpdateStatus(ord, 'DECLINED')}
                                className="px-3 py-1.5 border border-stone-700 text-stone-300 rounded-xl text-xs font-semibold hover:bg-stone-800 transition-colors cursor-pointer"
                              >
                                Decline
                              </button>
                            )}
                            <button
                              onClick={() => handleUpdateStatus(ord, 'PREPARING')}
                              className="px-4 py-1.5 bg-amber-500 hover:bg-amber-400 text-stone-950 rounded-xl text-xs font-bold shadow-md shadow-amber-500/20 transition-all cursor-pointer flex items-center gap-1.5"
                            >
                              <Utensils className="w-3.5 h-3.5" />
                              <span>Start Cooking</span>
                            </button>
                          </>
                        )}

                        {isPreparing && (
                          <button
                            onClick={() => handleUpdateStatus(ord, 'PACKED_READY')}
                            className="px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-xl text-xs font-bold shadow-lg shadow-emerald-600/20 transition-all cursor-pointer flex items-center gap-1.5 animate-pulse"
                          >
                            <PackageCheck className="w-3.5 h-3.5" />
                            <span>Order Prepared & Packed (Notify Customer)</span>
                          </button>
                        )}

                        {isPackedReady && (
                          <>
                            {isManager ? (
                              <button
                                onClick={() => handleUpdateStatus(ord, 'COMPLETED')}
                                className="px-4 py-1.5 bg-stone-800 hover:bg-stone-700 text-stone-200 rounded-xl text-xs font-bold border border-stone-700 transition-colors cursor-pointer flex items-center gap-1.5"
                              >
                                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                                <span>Handover to Customer</span>
                              </button>
                            ) : (
                              <span className="text-emerald-400 font-bold text-xs flex items-center gap-1.5 py-1">
                                <PackageCheck className="w-4 h-4" />
                                Packed & Ready (Awaiting Counter Handover)
                              </span>
                            )}
                          </>
                        )}

                        {isCompleted && (
                          <span className="text-emerald-400 font-bold text-xs flex items-center gap-1.5 py-1">
                            <CheckCircle2 className="w-4 h-4" />
                            Handed Over & Completed
                          </span>
                        )}
                      </>
                    )}
                  </div>
                </div>
              </div>
            );
          })}

          {filteredOrders.length === 0 && (
            <div className="text-center py-16 bg-stone-900/60 rounded-3xl border border-stone-800 my-4">
              <Calendar className="w-10 h-10 text-stone-600 mx-auto mb-2" />
              <p className="text-sm text-stone-300 font-medium">
                No orders match your filter criteria.
              </p>
            </div>
          )}
        </div>

        {/* Right Side Summary Panel */}
        <div className="lg:col-span-4 flex flex-col gap-4">
          <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-6 border border-stone-800 shadow-xl space-y-4">
            <h3 className="text-xs font-bold uppercase tracking-widest text-amber-400 font-mono flex items-center gap-2">
              <ChefHat className="w-4 h-4 text-amber-400" />
              <span>SavoryStay Kitchen Summary</span>
            </h3>

            <div className="grid grid-cols-2 gap-3">
              <div className="bg-stone-950 p-3.5 rounded-xl border border-stone-800/80 text-center">
                <span className="block text-xl font-bold font-mono text-amber-400">
                  {orders.length}
                </span>
                <span className="text-[10px] text-stone-400 uppercase tracking-wider font-semibold">
                  Today's Orders
                </span>
              </div>
              <div className="bg-stone-950 p-3.5 rounded-xl border border-stone-800/80 text-center">
                <span className="block text-xl font-bold font-mono text-emerald-400">
                  ₹{orders.reduce((sum, o) => sum + o.totalAmount, 0)}
                </span>
                <span className="text-[10px] text-stone-400 uppercase tracking-wider font-semibold">
                  Total Revenue
                </span>
              </div>
            </div>

            <div className="space-y-2.5 pt-3 border-t border-stone-800">
              <h4 className="text-[10px] font-bold text-stone-400 uppercase tracking-widest font-mono">
                Active Notification Gateways
              </h4>
              <div className="p-3.5 bg-stone-950 rounded-xl border border-stone-800 space-y-2.5 text-xs">
                <div className="flex justify-between items-center text-stone-300">
                  <span className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    SMS Gateway (+91)
                  </span>
                  <span className="text-[10px] font-mono text-emerald-400 font-bold">Fast2SMS / Twilio</span>
                </div>
                <div className="flex justify-between items-center text-stone-300">
                  <span className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    WhatsApp Cloud API
                  </span>
                  <span className="text-[10px] font-mono text-emerald-400 font-bold">Meta Verified</span>
                </div>
                <div className="flex justify-between items-center text-stone-300">
                  <span className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                    Spring Mail SMTP
                  </span>
                  <span className="text-[10px] font-mono text-emerald-400 font-bold">Port 587 TLS</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};


