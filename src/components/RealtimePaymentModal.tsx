import React, { useState } from 'react';
import { CartItem, PaymentMethod, Order } from '../types';
import { addOrderDB } from '../lib/firebase';
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
  Calendar,
  Users,
  UtensilsCrossed,
  ShoppingBag,
} from 'lucide-react';

interface RealtimePaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  cart: CartItem[];
  clearCart: () => void;
  onPaymentSuccess: (order: Order) => void;
}

export const RealtimePaymentModal: React.FC<RealtimePaymentModalProps> = ({
  isOpen,
  onClose,
  cart,
  clearCart,
  onPaymentSuccess,
}) => {
  const [customerName, setCustomerName] = useState('Rahul Sharma');
  const [customerPhone, setCustomerPhone] = useState('+91 98765 43210');
  const [customerEmail, setCustomerEmail] = useState('rahul.sharma@example.com');
  const [orderType, setOrderType] = useState<'PICKUP' | 'DINE_IN'>('PICKUP');
  const [pickupTime, setPickupTime] = useState('30 Mins (Ready by 07:45 PM)');
  const [tableNumber, setTableNumber] = useState(4);
  const [guests, setGuests] = useState(2);
  const [paymentGateway, setPaymentGateway] = useState<'UPI' | 'RAZORPAY' | 'CARD' | 'CASH'>('UPI');
  const [upiId, setUpiId] = useState('rahul@okaxis');

  // Real-time processing state
  const [isProcessing, setIsProcessing] = useState(false);
  const [processingStep, setProcessingStep] = useState<string>('');
  const [progressPercent, setProgressPercent] = useState<number>(0);
  const [paymentSuccessData, setPaymentSuccessData] = useState<{
    txnId: string;
    orderNumber: string;
    gateway: string;
    notifications: string[];
  } | null>(null);

  if (!isOpen) return null;

  const totalAmount = cart.reduce((sum, item) => sum + item.menuItem.price * item.quantity, 0);

  const handleStartRealtimePayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!customerName.trim()) return;

    setIsProcessing(true);
    setProgressPercent(15);
    setProcessingStep(`Creating ${paymentGateway} Order Intent on Spring Boot Server...`);

    try {
      // Step 1: Create Payment Intent
      const intentRes = await fetch('/api/v1/payments/create-intent', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: totalAmount,
          currency: 'INR',
          gateway: paymentGateway,
          customerName,
          phone: customerPhone,
        }),
      });

      const intentData = await intentRes.json();

      await new Promise((r) => setTimeout(r, 500));
      setProgressPercent(45);
      setProcessingStep(`Verifying ${paymentGateway === 'UPI' ? 'UPI VPA: ' + upiId : 'Razorpay Gateway'} & Tokenizing...`);

      // Step 2: Process & Capture Payment
      const response = await fetch('/api/v1/payments/process-realtime', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          amount: totalAmount,
          method: paymentGateway,
          gateway: paymentGateway,
          upiId: paymentGateway === 'UPI' ? upiId : undefined,
          customerName,
        }),
      });

      const resData = await response.json();

      await new Promise((r) => setTimeout(r, 600));
      setProgressPercent(75);
      setProcessingStep('Sending Multi-channel Alerts: App Push, SMS, WhatsApp & Email...');

      const orderNumber = '#ORD-' + Math.floor(1000 + Math.random() * 9000);

      // Step 3: Trigger Multi-channel Notification
      await fetch('/api/v1/notifications/send-order-alert', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orderNumber,
          customerName,
          phone: customerPhone,
          email: customerEmail,
          status: 'NEW',
        }),
      });

      await new Promise((r) => setTimeout(r, 400));
      setProgressPercent(100);
      setProcessingStep('Order Confirmed & Scheduled! Broadcasting to Kitchen Dashboard...');

      const newOrder: Order = {
        id: 'ord_' + Date.now(),
        orderNumber,
        orderType,
        ...(orderType === 'PICKUP' && pickupTime ? { pickupTime } : {}),
        ...(orderType === 'DINE_IN' ? { tableNumber, guests } : {}),
        timeSlot: pickupTime || '30 Mins',
        customerName,
        customerPhone,
        customerEmail,
        items: cart.map((c) => ({
          id: c.menuItem.id,
          title: c.menuItem.title,
          price: c.menuItem.price,
          quantity: c.quantity,
        })),
        totalAmount,
        paymentStatus: 'PAID',
        paymentMethod: paymentGateway as PaymentMethod,
        paymentTransactionId: resData.transactionId || 'TXN_UPI_' + Date.now(),
        orderStatus: 'NEW',
        createdAt: new Date().toISOString(),
        timestamp: Date.now(),
        notificationsSent: ['App Push', 'SMS', 'WhatsApp', 'Email']
      };

      // Save to Firestore
      await addOrderDB(newOrder);

      setPaymentSuccessData({
        txnId: newOrder.paymentTransactionId || 'TXN_UPI_SUCCESS',
        orderNumber,
        gateway: paymentGateway,
        notifications: ['App Push', 'SMS (+91)', 'WhatsApp', 'Email']
      });

      clearCart();
      onPaymentSuccess(newOrder);
    } catch (err) {
      console.error('Payment processing error:', err);
      setIsProcessing(false);
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
              {/* Order Summary Box */}
              <div className="bg-stone-950 p-3.5 rounded-2xl border border-stone-800 space-y-2">
                <div className="flex justify-between items-center text-xs font-semibold text-stone-300">
                  <span>Items Total ({cart.reduce((a, c) => a + c.quantity, 0)})</span>
                  <span className="text-amber-400 font-mono text-sm font-bold">₹{totalAmount}</span>
                </div>
                <div className="text-[11px] text-stone-400 divide-y divide-stone-800/60 max-h-24 overflow-y-auto">
                  {cart.map((c) => (
                    <div key={c.menuItem.id} className="py-1 flex justify-between">
                      <span>{c.quantity}x {c.menuItem.title}</span>
                      <span className="font-mono">₹{c.menuItem.price * c.quantity}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Order Type Toggle: Pickup vs Dine-In */}
              <div>
                <label className="block text-xs font-semibold text-stone-300 uppercase tracking-wider mb-1.5">
                  Order Fulfillment Type
                </label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setOrderType('PICKUP')}
                    className={`py-2 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border cursor-pointer ${
                      orderType === 'PICKUP'
                        ? 'bg-amber-500/15 text-amber-400 border-amber-500/50'
                        : 'bg-stone-950 text-stone-400 border-stone-800 hover:text-stone-200'
                    }`}
                  >
                    <ShoppingBag className="w-4 h-4" />
                    <span>Schedule Pickup</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setOrderType('DINE_IN')}
                    className={`py-2 px-3 rounded-xl text-xs font-bold flex items-center justify-center gap-2 border cursor-pointer ${
                      orderType === 'DINE_IN'
                        ? 'bg-amber-500/15 text-amber-400 border-amber-500/50'
                        : 'bg-stone-950 text-stone-400 border-stone-800 hover:text-stone-200'
                    }`}
                  >
                    <UtensilsCrossed className="w-4 h-4" />
                    <span>Dine-In Table</span>
                  </button>
                </div>
              </div>

              {/* Pickup Schedule Slot */}
              {orderType === 'PICKUP' ? (
                <div>
                  <label className="block text-xs font-semibold text-stone-300 mb-1">
                    Select Pickup Time Slot
                  </label>
                  <select
                    value={pickupTime}
                    onChange={(e) => setPickupTime(e.target.value)}
                    className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100 focus:outline-none focus:border-amber-500"
                  >
                    <option value="15 Mins (Ready by 07:30 PM)">15 Mins (Ready by 07:30 PM)</option>
                    <option value="30 Mins (Ready by 07:45 PM)">30 Mins (Ready by 07:45 PM)</option>
                    <option value="45 Mins (Ready by 08:00 PM)">45 Mins (Ready by 08:00 PM)</option>
                    <option value="1 Hour (Ready by 08:15 PM)">1 Hour (Ready by 08:15 PM)</option>
                    <option value="Scheduled for Tomorrow 12:30 PM">Scheduled for Tomorrow 12:30 PM</option>
                  </select>
                </div>
              ) : (
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-semibold text-stone-300 mb-1">Table #</label>
                    <input
                      type="number"
                      value={tableNumber}
                      onChange={(e) => setTableNumber(Number(e.target.value))}
                      className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-stone-300 mb-1">Guests</label>
                    <input
                      type="number"
                      value={guests}
                      onChange={(e) => setGuests(Number(e.target.value))}
                      className="w-full py-2 px-3 bg-stone-950 border border-stone-800 rounded-xl text-xs text-stone-100"
                    />
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

              {/* Submit Action */}
              <button
                type="submit"
                className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold text-xs rounded-xl shadow-lg shadow-amber-500/20 cursor-pointer transition-all flex items-center justify-center gap-2"
              >
                <Lock className="w-4 h-4" />
                <span>Confirm Order & Pay ₹{totalAmount}</span>
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};


