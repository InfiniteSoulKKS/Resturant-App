import React from 'react';
import { ViewTab } from '../types';import { UtensilsCrossed,
  Sparkles,
  ClipboardList,
  ChefHat,
  Terminal,
  ShieldCheck,
  Users,
  CalendarClock,
  UserCog,
  LayoutDashboard,
} from 'lucide-react';
import { hasRole, isStaffRole, canManage } from '../lib/roles';

interface BottomNavProps {
  activeTab: ViewTab;
  setActiveTab: (tab: ViewTab) => void;
  userRole?: string | null;
  memberCount?: number;
}

export const BottomNav: React.FC<BottomNavProps> = ({ activeTab, setActiveTab, userRole, memberCount }) => {
  const isCustomer = !userRole || userRole === 'ROLE_CUSTOMER';
  const isSuperAdmin = userRole === 'ROLE_SUPER_ADMIN';
  const isStaff = isStaffRole(userRole);
  const isAdmin = hasRole(userRole, 'ROLE_ADMIN');

  return (
    <nav className="md:hidden bg-[#0c0a09]/95 backdrop-blur-lg fixed bottom-0 w-full z-40 flex justify-around items-center px-2 h-16 border-t border-stone-800/80 shadow-2xl">
      <button
        onClick={() => setActiveTab('customer_menu')}
        className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
          activeTab === 'customer_menu' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
        }`}
      >
        <UtensilsCrossed className={`w-5 h-5 ${activeTab === 'customer_menu' ? 'stroke-[2.5]' : ''}`} />
        <span className="text-[10px] mt-1 font-medium">Menu</span>
      </button>

      {canManage(userRole) && (
        <button
          onClick={() => setActiveTab('menu_management')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'menu_management' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <Sparkles className={`w-5 h-5 ${activeTab === 'menu_management' ? 'stroke-[2.5]' : ''}`} />
          <span className="text-[10px] mt-1 font-medium">Menu</span>
        </button>
      )}

      {canManage(userRole) && (
        <button
          onClick={() => setActiveTab('preorder_settings')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'preorder_settings' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <CalendarClock className={`w-5 h-5 ${activeTab === 'preorder_settings' ? 'stroke-[2.5]' : ''}`} />
          <span className="text-[10px] mt-1 font-medium">Pre-Orders</span>
        </button>
      )}

      <button
        onClick={() => setActiveTab('orders')}
        className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
          activeTab === 'orders' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
        }`}
      >
        <ClipboardList className={`w-5 h-5 ${activeTab === 'orders' ? 'stroke-[2.5]' : ''}`} />
        <span className="text-[10px] mt-1 font-medium">{isCustomer ? 'Orders' : 'Orders'}</span>
      </button>

      {isStaff && (
        <button
          onClick={() => setActiveTab('chef_prep')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'chef_prep' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <ChefHat className={`w-5 h-5 ${activeTab === 'chef_prep' ? 'stroke-[2.5]' : ''}`} />
          <span className="text-[10px] mt-1 font-medium">Prep</span>
        </button>
      )}

      {canManage(userRole) && (
        <button
          onClick={() => setActiveTab('dashboard')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'dashboard' ? 'text-violet-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <LayoutDashboard className={`w-5 h-5 ${activeTab === 'dashboard' ? 'stroke-[2.5]' : ''}`} />
          <span className="text-[10px] mt-1 font-medium">Dash</span>
        </button>
      )}

      {(isAdmin || isSuperAdmin) && (
        <button
          onClick={() => setActiveTab('staff_management')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'staff_management' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <Users className={`w-5 h-5 ${activeTab === 'staff_management' ? 'stroke-[2.5]' : ''}`} />
          <span className="text-[10px] mt-1 font-medium">Staff</span>
        </button>
      )}

      {(isAdmin || isSuperAdmin) && (
        <button
          onClick={() => setActiveTab('customer_memberships')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'customer_memberships' ? 'text-sky-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <div className="relative group">
            <UserCog className={`w-5 h-5 ${activeTab === 'customer_memberships' ? 'stroke-[2.5]' : ''}`} />
            {(memberCount ?? 0) > 0 && (
              <>
                <span className="absolute -top-1.5 -right-2 bg-sky-500 text-white font-bold text-[8px] min-w-[14px] h-3.5 px-0.5 rounded-full flex items-center justify-center leading-none">
                  {memberCount}
                </span>
                <span className="pointer-events-none absolute bottom-full right-0 mb-2 px-2.5 py-1 rounded-lg bg-stone-800 border border-stone-700 text-[10px] text-stone-200 whitespace-nowrap shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-150 z-50">
                  {memberCount} customer{memberCount !== 1 ? 's' : ''} joined
                  <span className="absolute top-full right-3 -mt-px border-4 border-transparent border-t-stone-800" />
                </span>
              </>
            )}
          </div>
          <span className="text-[10px] mt-1 font-medium">Members</span>
        </button>
      )}

      {isSuperAdmin && (
        <button
          onClick={() => setActiveTab('super_admin')}
          className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
            activeTab === 'super_admin' ? 'text-amber-400 font-semibold' : 'text-stone-400 hover:text-stone-200'
          }`}
        >
          <ShieldCheck className={`w-5 h-5 ${activeTab === 'super_admin' ? 'stroke-[2.5]' : ''}`} />
          <span className="text-[10px] mt-1 font-medium">Admin</span>
        </button>
      )}

      <button
        onClick={() => setActiveTab('spring_backend')}
        className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
          activeTab === 'spring_backend' ? 'text-emerald-400 font-semibold' : 'text-stone-400 hover:text-emerald-300'
        }`}
      >
        <Terminal className={`w-5 h-5 ${activeTab === 'spring_backend' ? 'stroke-[2.5]' : ''}`} />
        <span className="text-[10px] mt-1 font-medium font-mono">API</span>
      </button>
    </nav>
  );
};