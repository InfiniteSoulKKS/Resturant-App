import { useState, useEffect } from 'react';
import { ViewTab, MenuItem, Order, PrepItem, CartItem } from './types';
import { Header } from './components/Header';
import { BottomNav } from './components/BottomNav';
import { CustomerMenuView } from './components/CustomerMenuView';
import { MenuManagement } from './components/MenuManagement';
import { PreBookingsDashboard } from './components/PreBookingsDashboard';
import { ChefPrepSummary } from './components/ChefPrepSummary';
import { BackendInspectorModal } from './components/BackendInspectorModal';
import { RealtimePaymentModal } from './components/RealtimePaymentModal';
import { AuthModal } from './components/AuthModal';
import {
  subscribeMenuItems,
  subscribeOrders,
  subscribePrepItems,
  seedInitialDataIfEmpty,
  testConnection,
} from './lib/firebase';
import { INITIAL_MENU_ITEMS, INITIAL_ORDERS, INITIAL_PREP_ITEMS } from './data/initialData';

export default function App() {
  const [activeTab, setActiveTab] = useState<ViewTab>('customer_menu');
  const [searchQuery, setSearchQuery] = useState('');
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);

  // Spring Security User & Token State
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [jwtToken, setJwtToken] = useState<string | null>(() => localStorage.getItem('savory_jwt_token'));

  // Firestore Real-time State
  const [menuItems, setMenuItems] = useState<MenuItem[]>(INITIAL_MENU_ITEMS);
  const [orders, setOrders] = useState<Order[]>(INITIAL_ORDERS);
  const [prepItems, setPrepItems] = useState<PrepItem[]>(INITIAL_PREP_ITEMS);

  // Cart State
  const [cart, setCart] = useState<CartItem[]>([
    { menuItem: INITIAL_MENU_ITEMS[2], quantity: 1 }, // Seared Scallops ($24)
    { menuItem: INITIAL_MENU_ITEMS[4], quantity: 1 }, // Molten Lava Cake ($12)
  ]);

  // Function to refresh JWT Access Token internally in the background
  const refreshSessionToken = async () => {
    const refreshToken = localStorage.getItem('savory_refresh_token');
    if (!refreshToken) return;

    try {
      const res = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (res.ok) {
        const data = await res.json();
        if (data.accessToken) {
          setJwtToken(data.accessToken);
          localStorage.setItem('savory_jwt_token', data.accessToken);
          if (data.refreshToken) {
            localStorage.setItem('savory_refresh_token', data.refreshToken);
          }
          console.log('[SPRING BOOT AUTH] 🔄 Internal background JWT access token refreshed successfully.');
        }
      } else {
        console.warn('[SPRING BOOT AUTH] Refresh token expired after 30 days. Signing out session.');
        handleLogout();
      }
    } catch (err) {
      console.error('[SPRING BOOT AUTH] Background refresh call error:', err);
    }
  };

  // Restore authenticated session on initial mount & setup background auto-refresh
  useEffect(() => {
    if (jwtToken) {
      fetch('/api/v1/auth/me', {
        headers: { Authorization: `Bearer ${jwtToken}` },
      })
        .then((res) => (res.ok ? res.json() : null))
        .then((data) => {
          if (data?.user) setCurrentUser(data.user);
          else {
            // Attempt refresh if access token expired
            refreshSessionToken();
          }
        })
        .catch(() => refreshSessionToken());
    } else {
      // If no access token but refresh token exists in localStorage (30-day persistence)
      const savedRefreshToken = localStorage.getItem('savory_refresh_token');
      if (savedRefreshToken) {
        refreshSessionToken();
      }
    }

    // Set internal periodic background refresh interval every 30 minutes
    const interval = setInterval(() => {
      refreshSessionToken();
    }, 30 * 60 * 1000);

    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    testConnection();
    seedInitialDataIfEmpty();

    const unsubMenu = subscribeMenuItems((items) => {
      if (items.length > 0) setMenuItems(items);
    });

    const unsubOrders = subscribeOrders((ords) => {
      if (ords.length > 0) setOrders(ords);
    });

    const unsubPrep = subscribePrepItems((preps) => {
      if (preps.length > 0) setPrepItems(preps);
    });

    return () => {
      unsubMenu();
      unsubOrders();
      unsubPrep();
    };
  }, []);

  const handleLoginSuccess = (user: any, token: string, refreshToken?: string) => {
    setCurrentUser(user);
    setJwtToken(token);
    localStorage.setItem('savory_jwt_token', token);
    if (refreshToken) {
      localStorage.setItem('savory_refresh_token', refreshToken);
    }
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setJwtToken(null);
    localStorage.removeItem('savory_jwt_token');
    localStorage.removeItem('savory_refresh_token');
  };

  const addToCart = (item: MenuItem) => {
    setCart((prev) => {
      const existingIndex = prev.findIndex((ci) => ci.menuItem.id === item.id);
      if (existingIndex > -1) {
        const updated = [...prev];
        updated[existingIndex].quantity += 1;
        return updated;
      }
      return [...prev, { menuItem: item, quantity: 1 }];
    });
  };

  const removeFromCart = (itemId: string) => {
    setCart((prev) => prev.filter((ci) => ci.menuItem.id !== itemId));
  };

  const clearCart = () => setCart([]);

  const totalCartCount = cart.reduce((sum, ci) => sum + ci.quantity, 0);

  const handlePaymentSuccess = (newOrder: Order) => {
    setActiveTab('orders');
  };

  return (
    <div className="bg-[#09090B] text-slate-100 min-h-screen antialiased flex flex-col selection:bg-indigo-500 selection:text-white">
      {/* Header */}
      <Header
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        searchQuery={searchQuery}
        setSearchQuery={setSearchQuery}
        cartCount={totalCartCount}
        onOpenCart={() => {
          if (cart.length > 0) setIsPaymentModalOpen(true);
        }}
        currentUser={currentUser}
        onOpenAuthModal={() => setIsAuthModalOpen(true)}
      />

      {/* Main Views */}
      <main className="flex-1">
        {activeTab === 'customer_menu' && (
          <CustomerMenuView
            menuItems={menuItems}
            searchQuery={searchQuery}
            setSearchQuery={setSearchQuery}
            cart={cart}
            addToCart={addToCart}
            removeFromCart={removeFromCart}
            onProceedToPayment={() => setIsPaymentModalOpen(true)}
          />
        )}

        {activeTab === 'menu_management' && <MenuManagement menuItems={menuItems} />}

        {activeTab === 'orders' && <PreBookingsDashboard orders={orders} />}

        {activeTab === 'chef_prep' && <ChefPrepSummary prepItems={prepItems} />}

        {activeTab === 'spring_backend' && <BackendInspectorModal />}
      </main>

      {/* Spring Security Auth Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => setIsAuthModalOpen(false)}
        currentUser={currentUser}
        onLoginSuccess={handleLoginSuccess}
        onLogout={handleLogout}
      />

      {/* Real-time Payment Checkout Modal */}
      <RealtimePaymentModal
        isOpen={isPaymentModalOpen}
        onClose={() => setIsPaymentModalOpen(false)}
        cart={cart}
        clearCart={clearCart}
        onPaymentSuccess={handlePaymentSuccess}
        currentUser={currentUser}
      />

      {/* Bottom Mobile Navigation */}
      <BottomNav activeTab={activeTab} setActiveTab={setActiveTab} />
    </div>
  );
}

