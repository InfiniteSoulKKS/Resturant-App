import React from 'react';
import { CartItem } from '../types';
import { AlertTriangle, XCircle, CheckCircle2, X, ShoppingCart, ArrowRight } from 'lucide-react';

interface AvailabilityWarningModalProps {
  isOpen: boolean;
  unavailableItems: CartItem[];
  availableItems: CartItem[];
  onRemoveUnavailable: (menuItemId: string) => void;
  onRemoveAllUnavailable: () => void;
  onProceedWithAvailable: () => void;
  onGoBackToMenu: () => void;
}

export const AvailabilityWarningModal: React.FC<AvailabilityWarningModalProps> = ({
  isOpen,
  unavailableItems,
  availableItems,
  onRemoveUnavailable,
  onRemoveAllUnavailable,
  onProceedWithAvailable,
  onGoBackToMenu,
}) => {
  if (!isOpen) return null;

  const unavailableTotal = unavailableItems.reduce(
    (sum, ci) => sum + ci.menuItem.price * ci.quantity,
    0
  );
  const availableTotal = availableItems.reduce(
    (sum, ci) => sum + ci.menuItem.price * ci.quantity,
    0
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onGoBackToMenu} />

      {/* Modal */}
      <div className="relative bg-stone-900 border border-stone-800 rounded-3xl shadow-2xl w-full max-w-lg max-h-[85vh] flex flex-col overflow-hidden">
        {/* Header */}
        <div className="bg-gradient-to-r from-rose-950/60 via-stone-900 to-stone-900 border-b border-stone-800 p-5 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-rose-500/15 border border-rose-500/30 flex items-center justify-center shrink-0">
            <AlertTriangle className="w-5 h-5 text-rose-400" />
          </div>
          <div className="flex-1">
            <h3 className="text-base font-bold text-stone-100">
              Some items are no longer available
            </h3>
            <p className="text-xs text-stone-400 mt-0.5">
              {unavailableItems.length} item{unavailableItems.length !== 1 ? 's' : ''} went out of stock since you added them to your cart.
            </p>
          </div>
          <button
            onClick={onGoBackToMenu}
            className="w-8 h-8 rounded-lg bg-stone-800 hover:bg-stone-700 flex items-center justify-center text-stone-400 hover:text-stone-200 transition-colors cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Unavailable items */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <h4 className="text-xs font-bold text-rose-400 uppercase tracking-wider flex items-center gap-1.5">
                <XCircle className="w-3.5 h-3.5" />
                Unavailable
              </h4>
              <button
                onClick={onRemoveAllUnavailable}
                className="text-[10px] font-semibold text-rose-400 hover:text-rose-300 underline underline-offset-2 cursor-pointer"
              >
                Remove all unavailable
              </button>
            </div>
            <div className="space-y-2">
              {unavailableItems.map((ci) => (
                <div
                  key={ci.menuItem.id}
                  className="flex items-center gap-3 bg-rose-950/30 border border-rose-800/40 rounded-xl p-3"
                >
                  <img
                    src={ci.menuItem.imageUrl}
                    alt={ci.menuItem.title}
                    className="w-12 h-12 rounded-lg object-cover grayscale opacity-60 shrink-0"
                    referrerPolicy="no-referrer"
                  />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-stone-200 line-through opacity-70 truncate">
                      {ci.menuItem.title}
                    </p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <span className="text-[10px] font-bold text-rose-400 bg-rose-500/10 px-1.5 py-0.5 rounded">
                        SOLD OUT
                      </span>
                      <span className="text-[11px] text-stone-500">
                        ×{ci.quantity} — ₹{ci.menuItem.price * ci.quantity}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => onRemoveUnavailable(ci.menuItem.id)}
                    className="w-8 h-8 rounded-lg bg-stone-800 hover:bg-stone-700 flex items-center justify-center text-stone-400 hover:text-rose-400 transition-colors cursor-pointer shrink-0"
                    title="Remove from cart"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
            </div>
            <p className="text-[10px] text-rose-400/70 mt-1.5 ml-1">
              Total unavailable: ₹{unavailableTotal}
            </p>
          </div>

          {/* Available items */}
          {availableItems.length > 0 && (
            <div>
              <h4 className="text-xs font-bold text-emerald-400 uppercase tracking-wider flex items-center gap-1.5 mb-2">
                <CheckCircle2 className="w-3.5 h-3.5" />
                Still Available
              </h4>
              <div className="space-y-2">
                {availableItems.map((ci) => (
                  <div
                    key={ci.menuItem.id}
                    className="flex items-center gap-3 bg-emerald-950/20 border border-emerald-800/30 rounded-xl p-3"
                  >
                    <img
                      src={ci.menuItem.imageUrl}
                      alt={ci.menuItem.title}
                      className="w-12 h-12 rounded-lg object-cover shrink-0"
                      referrerPolicy="no-referrer"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-stone-200 truncate">
                        {ci.menuItem.title}
                      </p>
                      <span className="text-[11px] text-stone-500">
                        ×{ci.quantity} — ₹{ci.menuItem.price * ci.quantity}
                      </span>
                    </div>
                    <CheckCircle2 className="w-4 h-4 text-emerald-500 shrink-0" />
                  </div>
                ))}
              </div>
              <p className="text-[10px] text-emerald-400/70 mt-1.5 ml-1">
                Available total: ₹{availableTotal}
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="border-t border-stone-800 p-5 bg-stone-950/50">
          <div className="flex flex-col sm:flex-row gap-3">
            <button
              onClick={onGoBackToMenu}
              className="flex-1 py-3 rounded-xl border border-stone-700 bg-stone-900 hover:bg-stone-800 text-stone-300 text-xs font-semibold transition-all cursor-pointer flex items-center justify-center gap-2"
            >
              <ShoppingCart className="w-4 h-4" />
              Back to Menu
            </button>
            {availableItems.length > 0 ? (
              <button
                onClick={onProceedWithAvailable}
                className="flex-1 py-3 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-stone-950 text-xs font-bold transition-all shadow-lg shadow-emerald-500/20 cursor-pointer flex items-center justify-center gap-2"
              >
                Proceed with {availableItems.length} item{availableItems.length !== 1 ? 's' : ''} — ₹{availableTotal}
                <ArrowRight className="w-4 h-4" />
              </button>
            ) : (
              <button
                onClick={onGoBackToMenu}
                className="flex-1 py-3 rounded-xl bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold transition-all shadow-lg shadow-amber-500/20 cursor-pointer flex items-center justify-center gap-2"
              >
                <ShoppingCart className="w-4 h-4" />
                Add items to your cart
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
