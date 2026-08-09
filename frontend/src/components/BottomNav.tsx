import React from 'react';
import { ViewTab } from '../types';
import { UtensilsCrossed, Sparkles, ClipboardList, ChefHat, Terminal } from 'lucide-react';

interface BottomNavProps {
  activeTab: ViewTab;
  setActiveTab: (tab: ViewTab) => void;
  currentUser?: any;
}

export const BottomNav: React.FC<BottomNavProps> = ({ activeTab, setActiveTab, currentUser }) => {
  const role = currentUser?.role;

  return (
    <nav className="md:hidden bg-[#0c0a09]/95 backdrop-blur-lg fixed bottom-0 w-full z-40 flex justify-around items-center px-2 h-16 border-t border-stone-800/80 shadow-2xl">
      {/* Customer / Guest Navigation */}
      {(!role || role === 'ROLE_CUSTOMER') && (
        <>
          <button
            onClick={() => setActiveTab('customer_menu')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'customer_menu'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <UtensilsCrossed className={`w-5 h-5 ${activeTab === 'customer_menu' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">Menu</span>
          </button>

          <button
            onClick={() => setActiveTab('orders')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'orders'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <ClipboardList className={`w-5 h-5 ${activeTab === 'orders' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">My Orders</span>
          </button>
        </>
      )}

      {/* Chef Navigation */}
      {role === 'ROLE_CHEF' && (
        <>
          <button
            onClick={() => setActiveTab('chef_prep')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'chef_prep'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <ChefHat className={`w-5 h-5 ${activeTab === 'chef_prep' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">Chef Prep</span>
          </button>

          <button
            onClick={() => setActiveTab('orders')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'orders'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <ClipboardList className={`w-5 h-5 ${activeTab === 'orders' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">Orders Queue</span>
          </button>
        </>
      )}

      {/* Manager / Admin Navigation */}
      {(role === 'ROLE_MANAGER' || role === 'ROLE_ADMIN') && (
        <>
          <button
            onClick={() => setActiveTab('orders')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'orders'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <ClipboardList className={`w-5 h-5 ${activeTab === 'orders' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">Orders</span>
          </button>

          <button
            onClick={() => setActiveTab('menu_management')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'menu_management'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <Sparkles className={`w-5 h-5 ${activeTab === 'menu_management' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">Menu Admin</span>
          </button>

          <button
            onClick={() => setActiveTab('chef_prep')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'chef_prep'
                ? 'text-amber-400 font-semibold'
                : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <ChefHat className={`w-5 h-5 ${activeTab === 'chef_prep' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium">Prep</span>
          </button>

          <button
            onClick={() => setActiveTab('spring_backend')}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all cursor-pointer ${
              activeTab === 'spring_backend'
                ? 'text-emerald-400 font-semibold'
                : 'text-stone-400 hover:text-emerald-300'
            }`}
          >
            <Terminal className={`w-5 h-5 ${activeTab === 'spring_backend' ? 'stroke-[2.5]' : ''}`} />
            <span className="text-[10px] mt-1 font-medium font-mono">Java</span>
          </button>
        </>
      )}
    </nav>
  );
};

