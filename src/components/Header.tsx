import React, { useState } from 'react';
import { ViewTab } from '../types';
import {
  UtensilsCrossed,
  Search,
  UserCheck,
  User,
  ShoppingBag,
  Terminal,
  X,
  Menu,
  ChefHat,
  ClipboardList,
  Flame,
  Sparkles,
} from 'lucide-react';

interface HeaderProps {
  activeTab: ViewTab;
  setActiveTab: (tab: ViewTab) => void;
  searchQuery: string;
  setSearchQuery: (q: string) => void;
  cartCount: number;
  onOpenCart: () => void;
  currentUser?: any;
  onOpenAuthModal?: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  setActiveTab,
  searchQuery,
  setSearchQuery,
  cartCount,
  onOpenCart,
  currentUser,
  onOpenAuthModal,
}) => {
  const [isMobileSearchOpen, setIsMobileSearchOpen] = useState(false);

  return (
    <header className="bg-[#0c0a09]/90 backdrop-blur-md fixed top-0 w-full z-50 border-b border-stone-800/80 transition-colors duration-200">
      <div className="max-w-[1440px] mx-auto px-4 md:px-8 h-16 flex justify-between items-center">
        {/* Brand Logo & Title */}
        <div
          className="flex items-center gap-3 cursor-pointer select-none group"
          onClick={() => setActiveTab('customer_menu')}
        >
          <div className="w-9 h-9 bg-gradient-to-br from-amber-500 to-amber-700 rounded-xl flex items-center justify-center text-stone-950 shadow-lg shadow-amber-500/20 group-hover:scale-105 transition-transform">
            <UtensilsCrossed className="w-5 h-5 text-stone-950 stroke-[2.5]" />
          </div>
          <div className="flex flex-col">
            <h1 className="text-xl md:text-2xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-1.5">
              SavoryStay
              <span className="text-[10px] uppercase font-sans font-bold px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-400 border border-amber-500/20 tracking-wider">
                Luxury
              </span>
            </h1>
          </div>
        </div>

        {/* Desktop Navigation Links */}
        <nav className="hidden md:flex items-center gap-1.5 bg-stone-900/60 p-1 rounded-2xl border border-stone-800/80">
          <button
            onClick={() => setActiveTab('customer_menu')}
            className={`text-xs font-medium transition-all py-1.5 px-3.5 rounded-xl cursor-pointer flex items-center gap-2 ${
              activeTab === 'customer_menu'
                ? 'bg-amber-500/15 text-amber-400 font-semibold border border-amber-500/30 shadow-sm'
                : 'text-stone-400 hover:text-stone-200 hover:bg-stone-800/50'
            }`}
          >
            <UtensilsCrossed className="w-3.5 h-3.5" />
            <span>Menu</span>
          </button>

          <button
            onClick={() => setActiveTab('menu_management')}
            className={`text-xs font-medium transition-all py-1.5 px-3.5 rounded-xl cursor-pointer flex items-center gap-2 ${
              activeTab === 'menu_management'
                ? 'bg-amber-500/15 text-amber-400 font-semibold border border-amber-500/30 shadow-sm'
                : 'text-stone-400 hover:text-stone-200 hover:bg-stone-800/50'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5" />
            <span>Menu Management</span>
          </button>

          <button
            onClick={() => setActiveTab('orders')}
            className={`text-xs font-medium transition-all py-1.5 px-3.5 rounded-xl cursor-pointer flex items-center gap-2 ${
              activeTab === 'orders'
                ? 'bg-amber-500/15 text-amber-400 font-semibold border border-amber-500/30 shadow-sm'
                : 'text-stone-400 hover:text-stone-200 hover:bg-stone-800/50'
            }`}
          >
            <ClipboardList className="w-3.5 h-3.5" />
            <span>Orders & Schedule</span>
          </button>

          <button
            onClick={() => setActiveTab('chef_prep')}
            className={`text-xs font-medium transition-all py-1.5 px-3.5 rounded-xl cursor-pointer flex items-center gap-2 ${
              activeTab === 'chef_prep'
                ? 'bg-amber-500/15 text-amber-400 font-semibold border border-amber-500/30 shadow-sm'
                : 'text-stone-400 hover:text-stone-200 hover:bg-stone-800/50'
            }`}
          >
            <ChefHat className="w-3.5 h-3.5" />
            <span>Chef Prep</span>
          </button>

          <button
            onClick={() => setActiveTab('spring_backend')}
            className={`text-xs font-mono py-1.5 px-3 rounded-xl transition-all cursor-pointer flex items-center gap-2 border ${
              activeTab === 'spring_backend'
                ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40'
                : 'bg-stone-900 text-stone-400 hover:text-stone-200 border-stone-800'
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
            <span>Java / Spring Architecture</span>
          </button>
        </nav>

        {/* Controls & User Profile */}
        <div className="flex items-center gap-2.5">
          {/* Desktop Search Bar */}
          <div className="relative hidden sm:block w-48 lg:w-60">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-stone-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search delicacies..."
              className="w-full pl-9 pr-8 py-1.5 bg-stone-900/90 rounded-xl border border-stone-800 text-stone-200 placeholder-stone-500 focus:outline-none focus:border-amber-500/70 focus:ring-1 focus:ring-amber-500/70 text-xs transition-colors"
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

          {/* Mobile Search Toggle Button */}
          <button
            onClick={() => setIsMobileSearchOpen(!isMobileSearchOpen)}
            className="sm:hidden p-2 rounded-xl text-stone-400 hover:text-stone-200 hover:bg-stone-800/60"
          >
            <Search className="w-5 h-5" />
          </button>

          {/* Spring Security User Auth Profile Button */}
          <button
            onClick={onOpenAuthModal}
            className="flex items-center gap-2 py-1.5 px-3 rounded-xl bg-stone-900 border border-stone-800 hover:border-amber-500/40 text-stone-200 text-xs transition-all cursor-pointer shadow-sm group"
            title="Spring Security Auth"
          >
            {currentUser ? (
              <UserCheck className="w-4 h-4 text-emerald-400" />
            ) : (
              <User className="w-4 h-4 text-amber-400 group-hover:scale-110 transition-transform" />
            )}
            <span className="hidden sm:inline font-medium max-w-[100px] truncate">
              {currentUser ? currentUser.username : 'Sign In'}
            </span>
            {currentUser && (
              <span className="w-2 h-2 rounded-full bg-emerald-400"></span>
            )}
          </button>

          {/* Cart Button */}
          <button
            onClick={onOpenCart}
            className="relative p-2 rounded-xl text-stone-300 hover:text-stone-100 hover:bg-stone-800/80 transition-all cursor-pointer bg-stone-900 border border-stone-800"
            title="View Cart & Checkout"
          >
            <ShoppingBag className="w-5 h-5 text-amber-400" />
            {cartCount > 0 && (
              <span className="absolute -top-1.5 -right-1.5 bg-amber-500 text-stone-950 font-bold text-[10px] w-5 h-5 rounded-full flex items-center justify-center shadow-md animate-pulse">
                {cartCount}
              </span>
            )}
          </button>

          {/* Mobile Backend Inspector Toggle */}
          <button
            onClick={() => setActiveTab('spring_backend')}
            className="md:hidden p-2 rounded-xl text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 hover:bg-emerald-500/20"
            title="Spring Boot Specifications"
          >
            <Terminal className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Mobile Expandable Search Drawer */}
      {isMobileSearchOpen && (
        <div className="sm:hidden px-4 py-2.5 bg-stone-900 border-b border-stone-800 flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-stone-400" />
            <input
              type="text"
              autoFocus
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search dishes or ingredients..."
              className="w-full pl-9 pr-8 py-2 bg-stone-950 rounded-xl border border-stone-800 text-stone-200 placeholder-stone-500 text-xs focus:outline-none focus:border-amber-500"
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
          <button
            onClick={() => setIsMobileSearchOpen(false)}
            className="text-xs text-amber-400 font-semibold px-2 py-1"
          >
            Cancel
          </button>
        </div>
      )}
    </header>
  );
};

