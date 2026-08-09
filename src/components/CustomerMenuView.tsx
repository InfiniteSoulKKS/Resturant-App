import React, { useState } from 'react';
import { MenuItem, Category, CartItem } from '../types';
import {
  Plus,
  Minus,
  ShoppingBag,
  Flame,
  Sparkles,
  Search,
  ArrowRight,
  SearchX,
  ChefHat,
  Clock,
  Award,
} from 'lucide-react';

interface CustomerMenuViewProps {
  menuItems: MenuItem[];
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  cart: CartItem[];
  addToCart: (item: MenuItem) => void;
  removeFromCart: (itemId: string) => void;
  onProceedToPayment: () => void;
}

const CATEGORIES: Category[] = ['All Items', 'Starters', 'Mains', 'Breads', 'Desserts', 'Beverages'];

export const CustomerMenuView: React.FC<CustomerMenuViewProps> = ({
  menuItems,
  searchQuery,
  setSearchQuery,
  cart,
  addToCart,
  removeFromCart,
  onProceedToPayment,
}) => {
  const [selectedCategory, setSelectedCategory] = useState<Category>('All Items');
  const [dietFilter, setDietFilter] = useState<'ALL' | 'VEG' | 'NON_VEG'>('ALL');

  const filteredItems = menuItems.filter((item) => {
    const matchesSearch =
      item.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.description.toLowerCase().includes(searchQuery.toLowerCase());

    const matchesCategory =
      selectedCategory === 'All Items' ||
      (selectedCategory === 'Starters' && (item.category === 'Starters' || item.category === 'Appetizers')) ||
      item.category === selectedCategory;

    const matchesDiet =
      dietFilter === 'ALL' ||
      (dietFilter === 'VEG' && item.isVeg === true) ||
      (dietFilter === 'NON_VEG' && item.isVeg === false);

    return matchesSearch && matchesCategory && matchesDiet;
  });

  const totalItemCount = cart.reduce((sum, ci) => sum + ci.quantity, 0);
  const totalCartPrice = cart.reduce((sum, ci) => sum + ci.menuItem.price * ci.quantity, 0);

  return (
    <div className="pt-20 px-4 md:px-8 max-w-[1440px] mx-auto pb-36 md:pb-28">
      {/* Hero Welcome Banner */}
      <div className="relative rounded-3xl overflow-hidden bg-gradient-to-r from-stone-900 via-stone-900/90 to-amber-950/40 border border-stone-800 p-6 md:p-8 mb-8 shadow-2xl">
        <div className="absolute -right-10 -bottom-10 w-80 h-80 bg-amber-500/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 text-xs font-semibold mb-3">
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            <span>Artisanal Culinary Pre-Booking & Pickup</span>
          </div>
          <h2 className="text-2xl md:text-4xl font-bold font-serif text-stone-100 tracking-tight leading-tight">
            Crafted Delicacies, Scheduled for Perfection
          </h2>
          <p className="text-xs md:text-sm text-stone-400 mt-2 leading-relaxed">
            Explore our chef-crafted menu. Pre-order ahead with live kitchen prep tracking and instant multi-channel order updates.
          </p>

          <div className="flex flex-wrap items-center gap-4 mt-5 text-xs text-stone-300">
            <div className="flex items-center gap-1.5">
              <Award className="w-4 h-4 text-amber-400" />
              <span>Michelin-Inspired Chefs</span>
            </div>
            <div className="flex items-center gap-1.5">
              <Clock className="w-4 h-4 text-amber-400" />
              <span>Guaranteed Slot Pickups</span>
            </div>
            <div className="flex items-center gap-1.5">
              <ChefHat className="w-4 h-4 text-amber-400" />
              <span>100% Fresh Daily Prep</span>
            </div>
          </div>
        </div>
      </div>

      {/* Categories & Filter Bar */}
      <div className="mb-8 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-stone-900/60 p-2 md:p-3 rounded-2xl border border-stone-800/80">
          {/* Categories Pills */}
          <div className="flex space-x-2 overflow-x-auto hide-scrollbar py-1">
            {CATEGORIES.map((cat) => {
              const isActive = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`flex-shrink-0 px-4 py-2 rounded-xl text-xs font-medium tracking-wide whitespace-nowrap transition-all cursor-pointer ${
                    isActive
                      ? 'bg-amber-500 text-stone-950 font-bold shadow-md shadow-amber-500/20'
                      : 'bg-stone-900 text-stone-400 hover:text-stone-100 hover:bg-stone-800/80 border border-stone-800'
                  }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>

          {/* Veg / Non-Veg Toggle Bar */}
          <div className="flex items-center bg-stone-950 p-1 rounded-xl border border-stone-800 text-xs font-medium self-start sm:self-auto">
            <button
              onClick={() => setDietFilter('ALL')}
              className={`px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                dietFilter === 'ALL' ? 'bg-stone-800 text-stone-100 font-bold' : 'text-stone-400 hover:text-stone-200'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setDietFilter('VEG')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                dietFilter === 'VEG' ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-800/80 font-bold' : 'text-stone-400 hover:text-emerald-400'
              }`}
            >
              <span className="w-2.5 h-2.5 rounded-sm border border-emerald-500 flex items-center justify-center p-0.5">
                <span className="w-1 h-1 rounded-full bg-emerald-500"></span>
              </span>
              Pure Veg
            </button>
            <button
              onClick={() => setDietFilter('NON_VEG')}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all cursor-pointer ${
                dietFilter === 'NON_VEG' ? 'bg-rose-950/80 text-rose-400 border border-rose-800/80 font-bold' : 'text-stone-400 hover:text-rose-400'
              }`}
            >
              <span className="w-2.5 h-2.5 rounded-sm border border-rose-500 flex items-center justify-center p-0.5">
                <span className="w-1 h-1 rounded-full bg-rose-500"></span>
              </span>
              Non-Veg
            </button>
          </div>
        </div>
      </div>

      {/* Menu Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {filteredItems.map((item) => {
          const cartEntry = cart.find((c) => c.menuItem.id === item.id);
          const quantityInCart = cartEntry ? cartEntry.quantity : 0;
          const isSoldOut = item.status === 'Sold Out';

          return (
            <div
              key={item.id}
              className={`bg-stone-900/80 backdrop-blur-md rounded-2xl border border-stone-800 hover:border-stone-700 transition-all duration-300 overflow-hidden flex flex-col h-full shadow-xl hover:shadow-2xl hover:-translate-y-1 group ${
                isSoldOut ? 'opacity-60' : ''
              }`}
            >
              {/* Image Container */}
              <div className="relative w-full h-48 bg-stone-950 overflow-hidden">
                <img
                  src={item.imageUrl}
                  alt={item.title}
                  className={`w-full h-full object-cover transition-transform duration-500 group-hover:scale-105 ${
                    isSoldOut ? 'grayscale' : ''
                  }`}
                  referrerPolicy="no-referrer"
                />

                {/* Gradient overlay */}
                <div className="absolute inset-0 bg-gradient-to-t from-stone-950/80 via-transparent to-transparent"></div>

                {/* Dietary Icon Badge */}
                <div className="absolute top-3 left-3 bg-stone-950/90 backdrop-blur-md px-2 py-1 rounded-lg border border-stone-700/60 shadow-md flex items-center gap-1.5">
                  <div className={`w-3.5 h-3.5 border flex items-center justify-center p-0.5 ${item.isVeg !== false ? 'border-emerald-500' : 'border-rose-500'}`}>
                    <div className={`w-1.5 h-1.5 rounded-full ${item.isVeg !== false ? 'bg-emerald-500' : 'bg-rose-500'}`}></div>
                  </div>
                  <span className="text-[10px] font-bold text-stone-300 uppercase tracking-wider">
                    {item.isVeg !== false ? 'VEG' : 'NON-VEG'}
                  </span>
                </div>

                {/* Category Tag */}
                <div className="absolute top-3 right-3 bg-stone-950/80 backdrop-blur-md px-2.5 py-1 rounded-lg border border-stone-700/50 shadow-md">
                  <span className="text-[10px] text-amber-400 font-bold uppercase tracking-wider">
                    {item.tag || item.category}
                  </span>
                </div>

                {isSoldOut && (
                  <div className="absolute bottom-3 left-3 bg-rose-950/90 text-rose-400 border border-rose-800/80 text-[10px] uppercase font-bold px-2 py-1 rounded-lg shadow">
                    Sold Out
                  </div>
                )}
              </div>

              {/* Card Details Body */}
              <div className="p-5 flex flex-col flex-grow">
                <div className="flex justify-between items-start mb-2">
                  <h3 className="text-base font-bold font-serif text-stone-100 group-hover:text-amber-400 transition-colors line-clamp-1">
                    {item.title}
                  </h3>
                  <span className="text-base font-bold font-mono text-amber-400 ml-2 whitespace-nowrap">
                    ₹{item.price}
                  </span>
                </div>

                <p className="text-xs text-stone-400 line-clamp-2 mb-4 flex-grow leading-relaxed">
                  {item.description}
                </p>

                {item.spiceLevel && (
                  <div className="flex items-center gap-1 mb-4 text-[11px] text-stone-400">
                    <Flame className="w-3.5 h-3.5 text-amber-500" />
                    <span>Spice Level:</span>
                    <span className="text-amber-400 font-semibold">{item.spiceLevel}</span>
                  </div>
                )}

                {/* Inline Quantity Controls or Add Button */}
                <div className="mt-auto">
                  {isSoldOut ? (
                    <button
                      disabled
                      className="w-full py-2.5 rounded-xl bg-stone-950 text-stone-600 border border-stone-800 text-xs font-semibold cursor-not-allowed text-center"
                    >
                      Currently Unavailable
                    </button>
                  ) : quantityInCart > 0 ? (
                    <div className="flex items-center justify-between bg-stone-950 border border-amber-500/40 p-1.5 rounded-xl">
                      <button
                        onClick={() => removeFromCart(item.id)}
                        className="w-8 h-8 rounded-lg bg-stone-800 hover:bg-stone-700 text-stone-200 flex items-center justify-center transition-colors cursor-pointer"
                        title="Reduce quantity"
                      >
                        <Minus className="w-4 h-4" />
                      </button>
                      <span className="font-bold font-mono text-sm text-amber-400 px-3">
                        {quantityInCart} in cart
                      </span>
                      <button
                        onClick={() => addToCart(item)}
                        className="w-8 h-8 rounded-lg bg-amber-500 hover:bg-amber-400 text-stone-950 flex items-center justify-center transition-colors font-bold cursor-pointer shadow-md shadow-amber-500/20"
                        title="Add another"
                      >
                        <Plus className="w-4 h-4 stroke-[3]" />
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => addToCart(item)}
                      className="w-full py-2.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold tracking-wide transition-all cursor-pointer shadow-lg shadow-amber-500/15 flex items-center justify-center gap-2 active:scale-[0.98]"
                    >
                      <Plus className="w-4 h-4 stroke-[2.5]" />
                      <span>Add to Order</span>
                    </button>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Empty State */}
      {filteredItems.length === 0 && (
        <div className="text-center py-16 bg-stone-900/60 rounded-3xl border border-stone-800 my-8">
          <SearchX className="w-12 h-12 text-stone-600 mx-auto mb-3" />
          <h3 className="text-base font-bold text-stone-200">
            No dishes found matching your criteria
          </h3>
          <p className="text-xs text-stone-500 mt-1 max-w-sm mx-auto">
            Try resetting your diet filter or search term to discover other handcrafted dishes.
          </p>
        </div>
      )}

      {/* Mobile Bottom Sticky Order Bar */}
      {cart.length > 0 && (
        <div className="fixed bottom-16 md:bottom-0 left-0 w-full bg-stone-950/95 backdrop-blur-xl border-t border-stone-800 z-30 px-4 py-3 md:hidden shadow-2xl">
          <div className="flex justify-between items-center mb-2">
            <div className="flex items-center gap-2">
              <ShoppingBag className="w-4 h-4 text-amber-400" />
              <span className="text-xs text-stone-300 font-medium">
                {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'} selected
              </span>
            </div>
            <span className="text-base font-bold text-amber-400 font-mono">
              ₹{totalCartPrice}
            </span>
          </div>
          <button
            onClick={onProceedToPayment}
            className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold rounded-xl flex items-center justify-center gap-2 transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
          >
            <span>Proceed to Checkout</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Desktop Floating Order Pill Bar */}
      {cart.length > 0 && (
        <div className="hidden md:flex fixed bottom-6 left-1/2 -translate-x-1/2 bg-stone-950/90 backdrop-blur-xl shadow-2xl rounded-full px-6 py-3 items-center space-x-6 z-30 border border-amber-500/30 amber-glow">
          <div className="flex items-center space-x-2">
            <ShoppingBag className="w-5 h-5 text-amber-400" />
            <span className="text-xs font-medium text-stone-200">
              {totalItemCount} {totalItemCount === 1 ? 'item' : 'items'}
            </span>
          </div>
          <span className="w-px h-5 bg-stone-800"></span>
          <span className="text-sm font-bold text-amber-400 font-mono">
            ₹{totalCartPrice}
          </span>
          <button
            onClick={onProceedToPayment}
            className="py-2.5 px-6 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold rounded-full transition-all cursor-pointer shadow-lg shadow-amber-500/20 flex items-center gap-2"
          >
            <span>Verify Order & Schedule Pickup</span>
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      )}
    </div>
  );
};


