import React from 'react';
import { ViewTab } from '../types';
import {
  UtensilsCrossed,
  Sparkles,
  ClipboardList,
  ChefHat,
  Terminal,
  ShieldCheck,
  Users,
  CalendarClock,
  UserCog,
  LayoutDashboard,
  Crown,
  BarChart3,
  ShoppingBag,
  ShoppingBasket,
} from 'lucide-react';
import { hasRole, isStaffRole, canManage } from '../lib/roles';

interface BottomNavProps {
  activeTab: ViewTab;
  setActiveTab: (tab: ViewTab) => void;
  userRole?: string | null;
  memberCount?: number;
}

/**
 * Mobile bottom navigation — maximum 5 items, role-adaptive.
 *
 * Customers:    Menu | Orders | API
 * Chef:         Menu | Orders | Prep | API
 * Manager:      Menu | Orders | Prep | Dash | API
 * Admin:        Menu | Orders | Staff | Admin | API
 * Super Admin:  Menu | Orders | Staff | Platform | API
 */
export const BottomNav: React.FC<BottomNavProps> = ({ activeTab, setActiveTab, userRole, memberCount }) => {
  const isCustomer = !userRole || userRole === 'ROLE_CUSTOMER';
  const isSuperAdmin = userRole === 'ROLE_SUPER_ADMIN';
  const isAdmin = hasRole(userRole, 'ROLE_ADMIN') && !isSuperAdmin;
  const isManager = hasRole(userRole, 'ROLE_MANAGER') && !isAdmin && !isSuperAdmin;
  const isChef = hasRole(userRole, 'ROLE_CHEF') && !isManager && !isAdmin && !isSuperAdmin;

  const items: {
    tab: ViewTab;
    label: string;
    icon: React.ReactNode;
    highlightColor: string;
  }[] = [];

  // ── Menu (everyone) ──
  items.push({
    tab: 'customer_menu',
    label: 'Menu',
    icon: <UtensilsCrossed className="w-5 h-5" />,
    highlightColor: 'text-amber-400',
  });

  if (isCustomer) {
    // Customer: Menu | Orders | API
    items.push({
      tab: 'orders',
      label: 'Orders',
      icon: <ClipboardList className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
  } else if (isChef) {
    // Chef: Menu | Orders | Prep | API
    items.push({
      tab: 'orders',
      label: 'Orders',
      icon: <ClipboardList className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'chef_prep',
      label: 'Prep',
      icon: <ChefHat className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
  } else if (isManager) {
    // Manager: Menu | Orders | Stock | Dash | API
    items.push({
      tab: 'orders',
      label: 'Orders',
      icon: <ClipboardList className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'ingredients',
      label: 'Stock',
      icon: <ShoppingBasket className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'dashboard',
      label: 'Dash',
      icon: <BarChart3 className="w-5 h-5" />,
      highlightColor: 'text-violet-400',
    });
  } else if (isAdmin) {
    // Admin: Menu | Orders | Staff | Admin Dashboard | API
    items.push({
      tab: 'orders',
      label: 'Orders',
      icon: <ClipboardList className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'staff_management',
      label: 'Staff',
      icon: <Users className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'admin_dashboard',
      label: 'Admin',
      icon: <Crown className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
  } else if (isSuperAdmin) {
    // Super Admin: Menu | Orders | Staff | Platform | API
    items.push({
      tab: 'orders',
      label: 'Orders',
      icon: <ClipboardList className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'staff_management',
      label: 'Staff',
      icon: <Users className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
    items.push({
      tab: 'super_admin',
      label: 'Platform',
      icon: <ShieldCheck className="w-5 h-5" />,
      highlightColor: 'text-amber-400',
    });
  }

  // ── API Inspector (everyone) ──
  items.push({
    tab: 'spring_backend',
    label: 'API',
    icon: <Terminal className="w-5 h-5" />,
    highlightColor: 'text-emerald-400',
  });

  return (
    <nav className="md:hidden bg-[#0c0a09]/95 backdrop-blur-lg fixed bottom-0 w-full z-40 flex justify-around items-center px-2 h-16 border-t border-stone-800/80 shadow-2xl">
      {items.map((item) => {
        const isActive = activeTab === item.tab;
        return (
          <button
            key={item.tab}
            onClick={() => setActiveTab(item.tab)}
            className={`flex flex-col items-center justify-center flex-1 py-1 rounded-xl transition-all duration-150 cursor-pointer ${
              isActive ? `${item.highlightColor} font-semibold` : 'text-stone-400 hover:text-stone-200'
            }`}
          >
            <span className={isActive ? 'stroke-[2.5]' : ''}>{item.icon}</span>
            <span className="text-[10px] mt-1 font-medium">{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
};
