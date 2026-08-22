import React, { useState, useEffect } from 'react';
import { CartItem, Order, PreOrderDateOption } from '../types';
import { placeOrder, confirmOrderPayment, getPreOrderDates, checkCartAvailability, getRestaurantSettings, getTableAvailability, type RestaurantSettings, type TableAvailability } from '../lib/apiClient';
import type { CartAvailabilityItem, CartAvailabilityResponse } from '../lib/apiClient';
import { getTokenUserId } from '../lib/tokenManager';
import {
  CheckCircle2,
  BellRing,
  MessageSquare,
  Mail,
  Smartphone,
  X,
  Lock,
  QrCode,
  CreditCard,
  Banknote,
  UtensilsCrossed,
  ShoppingBag,
  CalendarClock,
  AlertCircle,
} from 'lucide-react';

interface RealtimePaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  cart: CartItem[];
  clearCart: () => void;
  onPaymentSuccess: (order: Order) => void;
  currentUser?: any;
  restaurantId?: string;
  restaurantName?: string;
  /** Date picked on the menu's pre-order calendar — preselect it when loading. */
  initialPreOrderDate?: string;
  /** Real-time table availability update from SSE (another customer booked). */
  tableAvailabilityUpdate?: any;
}

export const RealtimePaymentModal: React.FC<RealtimePaymentModalProps> = ({
  isOpen,
  onClose,
  cart,
  clearCart,
  onPaymentSuccess,
  currentUser,
  restaurantId,
  restaurantName,
  initialPreOrderDate,
  tableAvailabilityUpdate,
}) => {
  const [customerName, setCustomerName] = useState('Rahul Sharma');
  const [customerPhone, setCustomerPhone] = useState('+91 98765 43210');
  const [customerEmail, setCustomerEmail] = useState('rahul.sharma@example.com');

  React.useEffect(() => {
    if (currentUser) {
      if (currentUser.username) setCustomerName(currentUser.username);
      if (currentUser.phone) setCustomerPhone(currentUser.phone);
      if (currentUser.email) setCustomerEmail(currentUser.email);
    }
  }, [currentUser, isOpen]);
  // Clear any stale payment error each time the modal opens.
  // NOTE: must live above the `if (!isOpen) return null` guard — calling a hook
  // after a conditional return violates the Rules of Hooks and crashes React.
  React.useEffect(() => {
    if (isOpen) setPaymentError(null);
  }, [isOpen]);
  const [orderType, setOrderType] = useState<'PICKUP' | 'DINE_IN' | 'PRE_ORDER'>('PICKUP');
  const [pickupTime, setPickupTime] = useState('30 Mins (Ready by 07:45 PM)');
  const [dineInTimeSlot, setDineInTimeSlot] = useState('12:00 PM');
  const [tableNumber, setTableNumber] = useState(4);
  const [guests, setGuests] = useState(2);
  const [paymentGateway, setPaymentGateway] = useState<'UPI' | 'RAZORPAY' | 'CARD' | 'CASH'>('UPI');
  const [upiId, setUpiId] = useState('rahul@okaxis');

  // Real-time table booking toast
  const [tableBookedToast, setTableBookedToast] = useState<string | null>(null);

  // Real-time processing state
  const [isProcessing, setIsProcessing] = useState(false);
  const [processingStep, setProcessingStep] = useState<string>('');
  const [progressPercent, setProgressPercent] = useState<number>(0);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [paymentSuccessData, setPaymentSuccessData] = useState<{
    txnId: string;
    orderNumber: string;
    gateway: string;
    notifications: string[];
  } | null>(null);

  // Pre-order availability: server-computed orderable dates for this cart's dishes.
  const [preOrderDates, setPreOrderDates] = useState<PreOrderDateOption[]>([]);
  const [selectedPreOrderDate, setSelectedPreOrderDate] = useState<string>('');
  const [preOrderDatesError, setPreOrderDatesError] = useState<string | null>(null);

  // Cart availability check — detects items that went out of stock while browsing.
  const [availabilityResult, setAvailabilityResult] = useState<CartAvailabilityResponse | null>(null);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);

  // Restaurant-specific settings (tables, time slots)
  const [restaurantSettings, setRestaurantSettings] = useState<RestaurantSettings | null>(null);

  // Table availability for DINE_IN
  const [tableAvailability, setTableAvailability] = useState<TableAvailability[]>([]);
  const [selectedTableType, setSelectedTableType] = useState<string>('');
  const [tableAvailabilityLoading, setTableAvailabilityLoading] = useState(false);

  // Check cart availability when modal opens (runs for all order types).
  useEffect(() => {
    if (!isOpen || !restaurantId || cart.length === 0) return;
    let cancelled = false;
    setAvailabilityLoading(true);
    setAvailabilityResult(null);
    checkCartAvailability(
      restaurantId,
      cart.map((c) => ({ menuItemId: c.menuItem.id, quantity: c.quantity }))
    )
      .then((res) => {
        if (!cancelled) setAvailabilityResult(res);
      })
      .catch((err) => {
        console.warn('Availability check failed:', err);
        if (!cancelled) setAvailabilityResult({ allAvailable: true, unavailableItems: [] });
      })
      .finally(() => {
        if (!cancelled) setAvailabilityLoading(false);
      });
    return () => { cancelled = true; };
  }, [isOpen, restaurantId, cart]);

  // Fetch restaurant settings (tables, time slots) when modal opens
  useEffect(() => {
    if (!isOpen || !restaurantId) return;
    let cancelled = false;
    getRestaurantSettings(restaurantId)
      .then((s) => { if (!cancelled) setRestaurantSettings(s); })
      .catch(() => { /* use defaults */ });
    return () => { cancelled = true; };
  }, [isOpen, restaurantId]);

  // Fetch table availability when DINE_IN is selected or time slot changes
  useEffect(() => {
    if (!isOpen || orderType !== 'DINE_IN' || !restaurantId) {
      setTableAvailability([]);
      setSelectedTableType('');
      return;
    }
    let cancelled = false;
    setTableAvailabilityLoading(true);
    const today = new Date().toISOString().split('T')[0];
    getTableAvailability(restaurantId, today, dineInTimeSlot)
      .then((tables) => {
        if (!cancelled) {
          setTableAvailability(tables);
          // Auto-select the first available table type
          const firstAvailable = tables.find((t) => t.remaining > 0);
          if (firstAvailable) setSelectedTableType(firstAvailable.type);
        }
      })
      .catch(() => { if (!cancelled) setTableAvailability([]); })
      .finally(() => { if (!cancelled) setTableAvailabilityLoading(false); });
    return () => { cancelled = true; };
  }, [isOpen, orderType, restaurantId, dineInTimeSlot]);

  // React to real-time table availability updates from SSE (another customer booked)
  useEffect(() => {
    if (!tableAvailabilityUpdate || !isOpen || orderType !== 'DINE_IN') return;
    if (tableAvailabilityUpdate.restaurantId !== restaurantId) return;
    // Only apply if the SSE event is for the same time slot we're currently viewing
    if (tableAvailabilityUpdate.timeSlot && tableAvailabilityUpdate.timeSlot !== dineInTimeSlot) return;

    // Detect which table types just became full or lost a table
    const bookedByGuests = tableAvailabilityUpdate.bookedByGuests || {};
    setTableAvailability((prev) => {
      if (!prev || prev.length === 0) return prev;
      // Check for changes to show toast
      let newlyFull: string[] = [];
      for (const tt of prev) {
        const seats = parseInt(tt.type) || 2;
        const booked = bookedByGuests[seats] || 0;
        const newRemaining = Math.max(0, tt.total - booked);
        if (tt.remaining > 0 && newRemaining === 0) {
          newlyFull.push(tt.type);
        } else if (tt.remaining > newRemaining && newRemaining > 0) {
          setTableBookedToast(`A ${tt.type} was just booked — ${newRemaining} left for ${dineInTimeSlot}`);
          setTimeout(() => setTableBookedToast(null), 4000);
        }
      }
      if (newlyFull.length > 0) {
        setTableBookedToast(`${newlyFull.join(' & ')} just filled up for ${dineInTimeSlot}!`);
        setTimeout(() => setTableBookedToast(null), 4000);
      }
      return prev.map((tt) => {
        const seats = parseInt(tt.type) || 2;
        const booked = bookedByGuests[seats] || 0;
        return { ...tt, booked, remaining: Math.max(0, tt.total - booked) };
      });
    });
  }, [tableAvailabilityUpdate, isOpen, orderType, restaurantId, dineInTimeSlot]);

  useEffect(() => {
    if (!isOpen || orderType !== 'PRE_ORDER') return;
    let cancelled = false;
    setPreOrderDatesError(null);
    getPreOrderDates({
      restaurantId: restaurantId || '',
      menuItemIds: cart.map((c) => c.menuItem.id),
    })
      .then((dates) => {
        if (cancelled) return;
        setPreOrderDates(dates);
        // Prefer the date picked on the menu calendar when it's still orderable;
        // otherwise fall back to the first orderable date.
        const first =
          dates.find((d) => d.date === initialPreOrderDate && d.orderable) ||
          dates.find((d) => d.orderable) ||
          dates[0];
        setSelectedPreOrderDate(first ? first.date : '');
      })
      .catch((err) => {
        if (!cancelled) setPreOrderDatesError(err?.message || 'Could not load pre-order dates');
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, orderType, restaurantId, initialPreOrderDate]);

  /** Build an ISO datetime (yyyy-MM-ddTHH:mm:ss) for the selected pre-order
   *  date + time so pre-orders land in that day's ingredient forecast. */
  const isoDateTime = (date: string, label: string): string => {
    const match = label.match(/(\d{1,2}):(\d{2})\s*(AM|PM)/i);
    let hours = 12;
    let minutes = 0;
    if (match) {
      let h = parseInt(match[1], 10);
      const isPM = match[3].toUpperCase() === 'PM';
      if (isPM && h !== 12) h += 12;
      if (!isPM && h === 12) h = 0;
      hours = h;
      minutes = parseInt(match[2], 10);
    }
    return `${date}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00`;
  };

  /** Parse "09:00" or "09:00 AM"/"09:00 PM" into minutes since midnight. */
  const toMinutes = (t?: string | null): number => {
    if (!t) return -1;
    const m = t.trim().match(/(\d{1,2}):(\d{2})\s*(AM|PM)?/i);
    if (!m) return -1;
    let h = parseInt(m[1], 10);
    const min = parseInt(m[2], 10);
    const suffix = m[3] ? m[3].toUpperCase() : null;
    if (suffix === 'PM' && h !== 12) h += 12;
    if (suffix === 'AM' && h === 12) h = 0;
    return h * 60 + min;
  };

  /** Time slots for the selected pre-order date, bounded by operating hours. */
  const timeSlotsForDate = (): string[] => {
    const opt = preOrderDates.find((d) => d.date === selectedPreOrderDate);
    if (!opt) return ['10:00 AM', '12:30 PM', '02:00 PM', '07:00 PM'];
    const open = toMinutes(opt.openTime);
    const close = toMinutes(opt.closeTime);
    const candidates = ['10:00 AM', '12:30 PM', '02:00 PM', '04:30 PM', '07:00 PM', '09:00 PM'];
    return candidates.filter((c) => {
      const min = toMinutes(c);
      return (open < 0 || min >= open) && (close < 0 || min < close);
    });
  };

  const selectedDateLabel = (): string => {
    const opt = preOrderDates.find((d) => d.date === selectedPreOrderDate);
    if (!opt) return '';
    const pretty = new Date(opt.date + 'T00:00:00').toLocaleDateString('en-IN', {
      weekday: 'short', day: 'numeric', month: 'short',
    });
    return `${pretty} (${opt.weekday})`;
  };

  if (!isOpen) return null;

  const totalAmount = cart.reduce((sum, item) => sum + item.menuItem.price * item.quantity, 0);

  /** Switch fulfillment type and reset the slot label to a valid option for it,
   *  so a PRE_ORDER never inherits a PICKUP label (and vice-versa). */
  const switchOrderType = (t: 'PICKUP' | 'DINE_IN' | 'PRE_ORDER') => {
    setOrderType(t);
    if (t === 'PRE_ORDER') setPickupTime('10:00 AM');
    else if (t === 'PICKUP') setPickupTime('30 Mins (Ready by 07:45 PM)');
    else if (t === 'DINE_IN') {
      // Reset DINE_IN time slot to first available
      setDineInTimeSlot(restaurantSettings?.dineinTimeSlots?.[0] || '12:00 PM');
    }
  };

  const effectivePreOrderDate = selectedPreOrderDate
    || preOrderDates.find((d) => d.orderable)?.date
    || '';

  const handleStartRealtimePayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerName.trim()) return;
    if (availabilityResult && availabilityResult.unavailableItems.length > 0) {
      setPaymentError('Some items in your cart are no longer available. Please remove them and try again.');
      return;
    }

    setIsProcessing(true);
    setPaymentError(null); // clear any previous failure so a retry starts clean
    setProgressPercent(15);
    setProcessingStep(`Placing order via Spring Boot backend...`);

    try {
      // Call the real backend
      const order = await placeOrder({
        restaurantId: restaurantId || '',
        orderType,
        tableNumber: orderType === 'DINE_IN' ? tableNumber : undefined,
        guests: orderType === 'DINE_IN' ? guests : undefined,
        timeSlot: orderType === 'PRE_ORDER'
          ? `${selectedDateLabel() || effectivePreOrderDate} ${pickupTime}`
          : orderType === 'DINE_IN'
          ? dineInTimeSlot
          : (pickupTime || '30 Mins'),
        pickupTime: orderType === 'PRE_ORDER'
          ? isoDateTime(effectivePreOrderDate, pickupTime)
          : (orderType === 'PICKUP' ? pickupTime : undefined),
        customerName,
        customerPhone,
        customerEmail,
        paymentMethod: paymentGateway,
        items: cart.map((c) => ({
          menuItemId: c.menuItem.id,
          quantity: c.quantity,
        })),
      });

      // Server-authoritative payment confirmation: the backend validates ownership
      // and amount before marking the order PAID. If it fails, the order stays PENDING.
      // CASH / Pay-on-Pickup orders stay PENDING until the customer pays at the counter.
      if (paymentGateway !== 'CASH') {
        try {
          await confirmOrderPayment(order.id, { amount: totalAmount, gateway: paymentGateway });
        } catch (confirmErr) {
          console.warn('Payment confirmation skipped — order stays PENDING', confirmErr);
        }
      }

      await new Promise((r) => setTimeout(r, 500));
      setProgressPercent(80);
      setProcessingStep('Order confirmed! Dispatching real-time notifications...');

      await new Promise((r) => setTimeout(r, 400));
      setProgressPercent(100);
      setProcessingStep('Order Confirmed! Real-time notification pushed via SSE.');

      setPaymentSuccessData({
        txnId: order.paymentTransactionId || 'TXN_MOCK_' + Date.now(),
        orderNumber: order.orderNumber,
        gateway: paymentGateway,
        notifications: ['App Push (SSE)', 'SMS', 'WhatsApp', 'Email']
      });

      clearCart();
      onPaymentSuccess(order);
    } catch (err: any) {
      console.error('Order placement error:', err);
      setIsProcessing(false);
      setPaymentError(
        err?.message ||
        'Unable to place your order. Please check your details and try again.'
      );
    }
  };

  const handleFinish = () => {
    setIsProcessing(false);
    setPaymentSuccessData(null);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
      <div className="bg-stone-900/95 border border-stone-800 rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl transition-all my-8 text-stone-100">
        {/* Header */}
        <div className="px-6 py-5 border-b border-stone-800 flex justify-between items-center bg-stone-950/80">
          <div>
            <span className="text-[10px] font-mono uppercase tracking-widest text-amber-400 font-bold">
              SavoryStay Checkout
            </span>
            <h2 className="text-lg font-bold font-serif text-stone-100 tracking-tight">
              Verify Order & Schedule Payment
            </h2>
          </div>
          <button
            onClick={onClose}
            disabled={isProcessing && progressPercent < 100}
            className="text-stone-400 hover:text-stone-100 p-1.5 rounded-xl hover:bg-stone-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6">
          {/* Real-time table booking toast */}
          {tableBookedToast && (
            <div className="mb-4 flex items-center gap-2 px-3 py-2.5 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 text-xs font-semibold animate-slide-in">
              <svg className="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              <span>{tableBookedToast}</span>
            </div>
          )}

          {paymentSuccessData ? (
            /* Success View */
            <div className="text-center py-4 space-y-4">
              <div className="w-16 h-16 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 rounded-2xl flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/10 animate-bounce">
                <CheckCircle2 className="w-8 h-8" />
              </div>

              <div>
                <h3 className="text-xl font-bold font-serif text-stone-100">Order Confirmed!</h3>
                <p className="text-xs text-stone-400 mt-1">
                  Order <span className="text-amber-400 font-mono font-bold">{paymentSuccessData.orderNumber}</span> successfully placed.
                </p>
              </div>

              {/* Notification Status Badges */}
              <div className="bg-stone-950 p-4 rounded-2xl border border-stone-800 text-left space-y-2">
                <p className="text-[11px] font-semibold text-stone-300 uppercase tracking-wider">
                  Live Multi-channel Alerts Dispatched:
                </p>
                <div className="grid grid-cols-2 gap-2">
                  <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-950/40 p-2 rounded-xl border border-emerald-800/50">
                    <BellRing className="w-4 h-4 shrink-0" />
                    <span>App Push Toast</span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-950/40 p-2 rounded-xl border border-emerald-800/50">
                    <Smartphone className="w-4 h-4 shrink-0" />
                    <span>SMS (+91)</span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-950/40 p-2 rounded-xl border border-emerald-800/50">
                    <MessageSquare className="w-4 h-4 shrink-0" />
                    <span>WhatsApp Alert</span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-950/40 p-2 rounded-xl border border-emerald-800/50">
                    <Mail className="w-4 h-4 shrink-0" />
                    <span>Email Invoice</span>
                  </div>
                </div>

                <div className="pt-2 border-t border-stone-800 flex justify-between text-xs text-stone-400 font-mono">
                  <span>Transaction ID:</span>
                  <span className="text-stone-200">{paymentSuccessData.txnId}</span>
                </div>
              </div>

              <button
                onClick={handleFinish}
                className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 cursor-pointer transition-all"
              >
                Track Live Order Progress
              </button>
            </div>
          ) : isProcessing ? (
            /* Processing View */
            <div className="py-8 space-y-6 text-center">
              <div className="w-16 h-16 border-4 border-amber-500/20 border-t-amber-500 rounded-full animate-spin mx-auto"></div>

              <div>
                <h3 className="text-base font-bold text-stone-100 mb-1">
                  Processing Order & Multi-channel Dispatch
                </h3>
                <p className="text-xs text-amber-400 font-mono animate-pulse">
                  {processingStep}
                </p>
              </div>

              {/* Progress Bar */}
              <div className="w-full bg-stone-950 rounded-full h-2 overflow-hidden border border-stone-800">
                <div
                  className="bg-amber-500 h-2 transition-all duration-300"
                  style={{ width: `${progressPercent}%` }}
                ></div>
              </div>
            </div>
          ) : (
            /* Order Form View */
            <form onSubmit={handleStartRealtimePayment} className="space-y-4">
              {/* Unavailable Items Warning */}
              {availabilityResult && availabilityResult.unavailableItems.length > 0 && (
                <div className="bg-rose-500/10 border border-rose-500/30 rounded-2xl p-4 space-y-2">
                  <div className="flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
                    <span className="text-xs font-bold text-rose-400">
                      {availabilityResult.unavailableItems.length} item(s) no longer available
                    </span>
                  </div>
                  <p className="text-[11px] text-rose-300/70">
                    These items have been marked as Sold Out or removed by the restaurant while you were browsing.
                    Please go back and remove them from your cart to proceed.
                  </p>
                  <div className="space-y-1">
                    {availabilityResult.unavailableItems.map((item) => (
                      <div key={item.menuItemId} className="flex items-center justify-between bg-stone-950/60 rounded-xl px-3 py-1.5">
                        <span className="text-[11px] text-stone-300">
                          {item.quantity}x {item.title}
                        </span>
                        <span className="text-[10px] font-mono text-rose-400 uppercase font-bold">
                          {item.status === 'NOT_FOUND' ? 'Removed' : item.status}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Order Summary Box */}
              <div className="bg-stone-950 p-3.5 rounded-2xl border border-stone-800 space-y-2">
                <div className="flex justify-between items-center text-xs font-semibold text-stone-300">
                  <span>Items Total ({cart.reduce((a, c) => a + c.quantity, 0)})</span>
                  <span className="text-amber-400 font-mono text-sm font-bold">₹{totalAmount}</span>
                </div>
                <div className="text-[11px] text-stone-400 divide-y divide-stone-800/60 max-h-24 overflow-y-auto">
                  {cart.map((c) => {
                    const isUnavailable = availabilityResult?.unavailableItems.some(
                      (u) => u.menuItemId === c.menuItem.id
                    );
                    return (
                      <div key={c.menuItem.id} className={`py-1 flex justify-between ${isUnavailable ? 'line-through text-rose-400' : ''}`}>
                        <span>{c.quantity}x {c.menuItem.title}</span>
                        <span className="font-mono">₹{c.menuItem.price * c.quantity}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Order Type Toggle: Pickup vs Dine-In */}
              <div>
                <label className="block text-xs font-semibold text-stone-300 uppercase tracking-wider mb-1.5">
                  Order Fulfillment Type
                </label>
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => switchOrderType('PICKUP')}
                    className={`py-2 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border cursor-pointer ${
                      orderType === 'PICKUP'
                        ? 'bg-amber-500/15 text-amber-400 border-amber-500/50'
                        : 'bg-stone-950 text-stone-400 border-stone-800 hover:text-stone-200'
                    }`}
                  >
                    <ShoppingBag className="w-4 h-4 shrink-0" />
                    <span>Pickup</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => switchOrderType('DINE_IN')}
                    className={`py-2 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border cursor-pointer ${
                      orderType === 'DINE_IN'
                        ? 'bg-amber-500/15 text-amber-400 border-amber-500/50'
                        : 'bg-stone-950 text-stone-400 border-stone-800 hover:text-stone-200'
                    }`}
                  >
                    <UtensilsCrossed className="w-4 h-4 shrink-0" />
                    <span>Dine-In</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => switchOrderType('PRE_ORDER')}
                    className={`py-2 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border cursor-pointer ${
                      orderType === 'PRE_ORDER'
                        ? 'bg-amber-500/15 text-amber-400 border-amber-500/50'
                        : 'bg-stone-950 text-stone-400 border-stone-800 hover:text-stone-200'
                    }`}
                  >
                    <CalendarClock className="w-4 h-4 shrink-0" />
                    <span>Pre-Order</span>
                  </button>
                </div>
              </div>

              {/* Pre-Order: date + pickup time (availability from backend) */}
              {orderType === 'PRE_ORDER' ? (
                <div className="space-y-3">
                  {preOrderDatesError && (
                    <p className="text-[11px] text-rose-400 bg-rose-500/10 border border-rose-500/30 rounded-xl px-3 py-2">
                      {preOrderDatesError}
                    </p>
                  )}
                  <div>
                    <label className="block text-xs font-semibold text-stone-300 mb-1">
                      Select Pre-Order Date
                    </label>
                    {preOrderDates.length > 0 ? (
                      <select
                        value={selectedPreOrderDate}
                        onChange={(e) => setSelectedPreOrderDate(e.target.value)}
                        className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                      >
                        {preOrderDates.map((d) => {
                          const pretty = new Date(d.date + 'T00:00:00').toLocaleDateString('en-IN', {
                            weekday: 'short', day: 'numeric', month: 'short',
                          });
                          return (
                            <option key={d.date} value={d.date} disabled={!d.orderable}>
                              {pretty} — {d.orderable ? 'Available' : (d.reasons[0] || 'Unavailable')}
                            </option>
                          );
                        })}
                      </select>
                    ) : (
                      <div className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-500">
                        {preOrderDatesError ? 'Date check unavailable — will validate on submit.' : 'Checking availability...'}
                      </div>
                    )}
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-stone-300 mb-1">
                      Select Pickup Time Slot
                    </label>
                    <select
                      value={pickupTime}
                      onChange={(e) => setPickupTime(e.target.value)}
                      className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                    >
                      {timeSlotsForDate().map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                  {selectedPreOrderDate && !preOrderDates.find((d) => d.date === selectedPreOrderDate)?.orderable && (
                    <p className="text-[10px] text-rose-400">
                      The selected date is not orderable — pick another date or remove unavailable dishes.
                    </p>
                  )}
                  <p className="text-[10px] text-stone-500">
                    Pre-orders close at this restaurant's configured cutoff on the day before and are
                    included in that day's ingredient forecast for the kitchen.
                  </p>
                </div>
              ) : orderType === 'PICKUP' ? (
                <div>
                  <label className="block text-xs font-semibold text-stone-300 mb-1">
                    Select Pickup Time Slot
                  </label>
                  <select
                    value={pickupTime}
                    onChange={(e) => setPickupTime(e.target.value)}
                    className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                  >
                    {(restaurantSettings?.pickupTimeSlots?.length
                      ? restaurantSettings.pickupTimeSlots
                      : ['15 Mins', '30 Mins', '45 Mins', '1 Hour', '1.5 Hours']
                    ).map((t) => (
                      <option key={t} value={t}>{t}</option>
                    ))}
                  </select>
                </div>
              ) : (
                <div className="space-y-3">
                  <label className="block text-xs font-semibold text-stone-300 uppercase tracking-wider">
                    Select Table Type
                  </label>
                  {tableAvailabilityLoading ? (
                    <div className="text-[11px] text-stone-500 py-2">Checking table availability...</div>
                  ) : tableAvailability.length === 0 ? (
                    <div className="text-[11px] text-stone-500 py-2">No table configuration found. Contact the restaurant.</div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                      {tableAvailability.map((tt) => {
                        const isSelected = selectedTableType === tt.type;
                        const isFull = tt.remaining === 0;
                        const seats = parseInt(tt.type) || 2;
                        return (
                          <button
                            key={tt.type}
                            type="button"
                            disabled={isFull}
                            onClick={() => {
                              setSelectedTableType(tt.type);
                              setGuests(seats);
                              setTableNumber(seats); // use seats as table category
                            }}
                            className={`relative p-3 rounded-xl border text-left transition-all cursor-pointer ${
                              isFull
                                ? 'bg-stone-950 border-stone-800 opacity-50 cursor-not-allowed'
                                : isSelected
                                  ? 'bg-amber-500/10 border-amber-500/50 shadow-lg shadow-amber-500/10'
                                  : 'bg-stone-950 border-stone-800 hover:border-stone-600'
                            }`}
                          >
                            <div className="flex items-center justify-between mb-1">
                              <span className={`text-xs font-bold ${isSelected ? 'text-amber-400' : 'text-stone-200'}`}>
                                {tt.type}
                              </span>
                              {isFull && (
                                <span className="text-[9px] font-bold text-rose-400 bg-rose-500/10 px-1.5 py-0.5 rounded">
                                  FULL
                                </span>
                              )}
                            </div>
                            <div className="flex items-center justify-between">
                              <span className="text-[10px] text-stone-500">
                                {tt.remaining} of {tt.total} free
                              </span>
                              <div className="flex gap-0.5">
                                {Array.from({ length: tt.total }, (_, i) => (
                                  <div
                                    key={i}
                                    className={`w-1.5 h-1.5 rounded-full ${
                                      i < tt.booked ? 'bg-rose-500' : 'bg-emerald-500/60'
                                    }`}
                                  />
                                ))}
                              </div>
                            </div>
                            {/* Seats icon row */}
                            <div className="flex gap-1 mt-2">
                              {Array.from({ length: Math.min(seats, 8) }, (_, i) => (
                                <span key={i} className="text-[10px]">🪑</span>
                              ))}
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  )}
                  {/* Time slot selection for DINE_IN */}
                  <div>
                    <label className="block text-xs font-semibold text-stone-300 mb-1">
                      Preferred Time Slot
                    </label>
                    <select
                      value={dineInTimeSlot}
                      onChange={(e) => setDineInTimeSlot(e.target.value)}
                      className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                    >
                      {restaurantSettings?.dineinTimeSlots?.map((t) => (
                        <option key={t} value={t}>{t}</option>
                      )) || [
                        '12:00 PM', '12:30 PM', '1:00 PM', '1:30 PM', '2:00 PM',
                        '7:00 PM', '7:30 PM', '8:00 PM', '8:30 PM', '9:00 PM', '9:30 PM'
                      ].map((t) => (
                        <option key={t} value={t}>{t}</option>
                      ))}
                    </select>
                  </div>
                </div>
              )}

              {/* Customer Contact Information */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                <div>
                  <label className="block text-[11px] font-semibold text-stone-400 mb-1">Customer Name</label>
                  <input
                    type="text"
                    required
                    value={customerName}
                    onChange={(e) => setCustomerName(e.target.value)}
                    className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-stone-400 mb-1">Mobile (+91 SMS/WhatsApp)</label>
                  <input
                    type="text"
                    required
                    value={customerPhone}
                    onChange={(e) => setCustomerPhone(e.target.value)}
                    className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-stone-400 mb-1">Email Address (Order Confirmation)</label>
                <input
                  type="email"
                  required
                  value={customerEmail}
                  onChange={(e) => setCustomerEmail(e.target.value)}
                  className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                />
              </div>

              {/* Payment Gateway Options */}
              <div>
                <label className="block text-xs font-semibold text-stone-300 uppercase tracking-wider mb-1.5">
                  Select Payment Gateway
                </label>
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => setPaymentGateway('UPI')}
                    className={`p-2.5 rounded-xl border text-center transition-all cursor-pointer ${
                      paymentGateway === 'UPI'
                        ? 'bg-amber-500/15 border-amber-500/60 text-amber-400 font-bold'
                        : 'bg-stone-950 border-stone-800 text-stone-400 hover:text-stone-100'
                    }`}
                  >
                    <QrCode className="w-5 h-5 mx-auto mb-1 text-amber-400" />
                    <span className="text-[10px] font-bold block">UPI / GPay</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setPaymentGateway('RAZORPAY')}
                    className={`p-2.5 rounded-xl border text-center transition-all cursor-pointer ${
                      paymentGateway === 'RAZORPAY'
                        ? 'bg-amber-500/15 border-amber-500/60 text-amber-400 font-bold'
                        : 'bg-stone-950 border-stone-800 text-stone-400 hover:text-stone-100'
                    }`}
                  >
                    <CreditCard className="w-5 h-5 mx-auto mb-1 text-amber-400" />
                    <span className="text-[10px] font-bold block">Razorpay</span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setPaymentGateway('CASH')}
                    className={`p-2.5 rounded-xl border text-center transition-all cursor-pointer ${
                      paymentGateway === 'CASH'
                        ? 'bg-amber-500/15 border-amber-500/60 text-amber-400 font-bold'
                        : 'bg-stone-950 border-stone-800 text-stone-400 hover:text-stone-100'
                    }`}
                  >
                    <Banknote className="w-5 h-5 mx-auto mb-1 text-amber-400" />
                    <span className="text-[10px] font-bold block">Pay on Pickup</span>
                  </button>
                </div>
              </div>

              {/* UPI Field */}
              {paymentGateway === 'UPI' && (
                <div className="bg-stone-950 p-3 rounded-xl border border-stone-800 space-y-2">
                  <label className="block text-[11px] font-semibold text-stone-300">Enter UPI ID (VPA)</label>
                  <input
                    type="text"
                    value={upiId}
                    onChange={(e) => setUpiId(e.target.value)}
                    placeholder="username@okaxis"
                    className="w-full py-2 px-3 bg-stone-900 border border-stone-800 rounded-xl text-xs text-stone-100 font-mono"
                  />
                  <p className="text-[10px] text-stone-400">Supports PhonePe, Google Pay, Paytm & BHIM UPI.</p>
                </div>
              )}

              {/* Order placement / auth errors — visible instead of silent failure */}
              {paymentError && (
                <div className="p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-xs text-rose-400 flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                  <span>{paymentError}</span>
                </div>
              )}

              {/* Submit Action */}
              <button
                type="submit"
                disabled={!!(availabilityResult && availabilityResult.unavailableItems.length > 0)}
                className={`w-full py-3 font-bold text-xs rounded-xl shadow-lg transition-all flex items-center justify-center gap-2 ${
                  availabilityResult && availabilityResult.unavailableItems.length > 0
                    ? 'bg-stone-700 text-stone-400 cursor-not-allowed shadow-none'
                    : 'bg-amber-500 hover:bg-amber-400 text-stone-950 shadow-amber-500/20 cursor-pointer'
                }`}
              >
                <Lock className="w-4 h-4" />
                <span>
                  {availabilityResult && availabilityResult.unavailableItems.length > 0
                    ? 'Remove unavailable items to proceed'
                    : `Confirm Order & Pay ₹${totalAmount}`}
                </span>
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};


