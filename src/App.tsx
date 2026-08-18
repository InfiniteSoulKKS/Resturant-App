import { useState, useEffect, useCallback, useRef } from 'react';
import { ViewTab, MenuItem, Order, CartItem, Restaurant, Notification } from './types';
import { Header } from './components/Header';
import { BottomNav } from './components/BottomNav';
import { CustomerMenuView } from './components/CustomerMenuView';
import { MenuManagement } from './components/MenuManagement';
import { PreBookingsDashboard } from './components/PreBookingsDashboard';
import { ChefPrepSummary } from './components/ChefPrepSummary';
import { BackendInspectorModal } from './components/BackendInspectorModal';
import { RealtimePaymentModal } from './components/RealtimePaymentModal';
import { AuthModal } from './components/AuthModal';
import { SuperAdminDashboard } from './components/SuperAdminDashboard';
import { StaffManagement } from './components/StaffManagement';
import { CustomerMembershipManager } from './components/CustomerMembershipManager';
import { IngredientPlanning } from './components/IngredientPlanning';
import { ManagerDashboard } from './components/ManagerDashboard';
import { PreOrderSettings } from './components/PreOrderSettings';
import { OrderTracking } from './components/OrderTracking';
import { NotificationsBell } from './components/NotificationsBell';
import { RestaurantPicker } from './components/RestaurantPicker';
import { RestaurantSelector } from './components/RestaurantSelector';
import { getToken, storeToken as storeAuthToken, removeToken, getTokenRole, getTokenRestaurantId, getTokenUserId, getTokenExpiryTime } from './lib/tokenManager';
import { listRestaurants, getPublicMenu, getRestaurantOrders, getMyOrders, getCurrentUser, listCustomerMembers } from './lib/apiClient';
import { useRealtimeNotifications } from './hooks/useRealtimeNotifications';
import { INITIAL_MENU_ITEMS, INITIAL_ORDERS, INITIAL_PREP_ITEMS } from './data/initialData';
import { hasRole, canManage } from './lib/roles';

export default function App() {
  const [activeTab, setActiveTab] = useState<ViewTab>('customer_menu');
  const [searchQuery, setSearchQuery] = useState('');
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [isAuthModalOpen, setIsAuthModalOpen] = useState(false);
  // Set when an unauthenticated user tries to check out — resume to the payment
  // modal automatically once they sign in.
  const [pendingCheckout, setPendingCheckout] = useState(false);

  // Auth state
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [jwtToken, setJwtToken] = useState<string | null>(() => getToken());
  const [userRole, setUserRole] = useState<string | null>(() => getTokenRole());
  const [userRestaurantId, setUserRestaurantId] = useState<string | null>(() => getTokenRestaurantId());

  // Restaurant selector (post-login for customers with multiple memberships)
  const [isRestaurantSelectorOpen, setIsRestaurantSelectorOpen] = useState(false);

  // Multi-tenant state
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [currentRestaurantId, setCurrentRestaurantId] = useState<string | null>(null);

  // Backend data
  const [menuItems, setMenuItems] = useState<MenuItem[]>(INITIAL_MENU_ITEMS);
  const [orders, setOrders] = useState<Order[]>([]);
  const [liveNotifications, setLiveNotifications] = useState<Notification[]>([]);

  // Cart state
  const [cart, setCart] = useState<CartItem[]>([]);

  // Date picked on the menu's pre-order calendar — checkout preselects it.
  const [preOrderDate, setPreOrderDate] = useState('');

  // Customer member count for the badge on the Members tab
  const [memberCount, setMemberCount] = useState<number>(0);

  // Realtime SSE: handle incoming notifications
  const handleRealtimeEvent = useCallback((data: any) => {
    if (data?.id) {
      setLiveNotifications((prev) => [...prev, data as Notification]);
    }
    // If the event contains order status update, refresh orders
    if (data?.type === 'ORDER_STATUS' || data?.type === 'ORDER_READY' || data?.type === 'NEW_ORDER') {
      refreshOrders();
      // Tell the customer's order-tracking view to re-fetch so status changes
      // appear live instead of requiring a manual reload.
      orderTrackingRefreshRef.current?.();
    }
  }, []);

  useRealtimeNotifications(handleRealtimeEvent, !!jwtToken);

  // OrderTracking registers a refresh callback here; realtime order events
  // invoke it so the customer's order list updates live (the SSE payload only
  // carries orderId/type, so the list is re-fetched rather than patched).
  const orderTrackingRefreshRef = useRef<(() => void) | null>(null);

  // Fetch restaurants on mount
  useEffect(() => {
    listRestaurants()
      .then((list) => {
        setRestaurants(list);
        if (list.length > 0 && !currentRestaurantId) {
          setCurrentRestaurantId(list[0].id);
        }
      })
      .catch(() => {
        // Fallback to seeded data
        setRestaurants([]);
      });
  }, []);

  // Fetch menu for current restaurant
  useEffect(() => {
    if (currentRestaurantId) {
      getPublicMenu(currentRestaurantId)
        .then(setMenuItems)
        .catch(() => {});
    }
  }, [currentRestaurantId]);

  // Fetch customer member count for the badge (admin/super-admin only)
  useEffect(() => {
    const role = getTokenRole();
    const isAdminOrSuperAdmin = role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN';
    if (isAdminOrSuperAdmin && currentRestaurantId) {
      listCustomerMembers(currentRestaurantId)
        .then((members) => setMemberCount(members.length))
        .catch(() => setMemberCount(0));
    } else {
      setMemberCount(0);
    }
  }, [currentRestaurantId, jwtToken]);

  // Fetch role-scoped orders on mount and when user changes.
  // Super admins have no restaurant in their JWT, so fall back to the
  // restaurant they picked in the header selector.
  const refreshOrders = useCallback(() => {
    const role = getTokenRole();
    if (!role || role === 'ROLE_CUSTOMER') {
      getMyOrders()
        .then(setOrders)
        .catch(() => {});
    } else {
      const rid = getTokenRestaurantId() || currentRestaurantId;
      if (rid) {
        getRestaurantOrders(rid)
          .then(setOrders)
          .catch(() => {});
      }
    }
  }, [currentRestaurantId]);

  useEffect(() => {
    if (jwtToken) {
      refreshOrders();
    }
  }, [jwtToken, refreshOrders]);

  // Restore session on mount. Also fetch the full profile so the UI (header,
  // checkout gate, payment prefill) agrees with the stored token — previously
  // a valid token left currentUser null, so the app looked logged-out while
  // checkout still worked.
  useEffect(() => {
    const token = getToken();
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        const expiryTime = payload.exp * 1000;
        if (expiryTime > Date.now()) {
          setJwtToken(token);
          setUserRole(payload.role);
          setUserRestaurantId(payload.restaurantId);
          getCurrentUser()
            .then((user) => {
              // Ignore a stale profile if the token changed mid-flight (e.g. the
              // user logged in as someone else before this resolved).
              if (getToken() === token) setCurrentUser(user);
            })
            .catch(() => {
              // Token invalid/revoked server-side — clear the stale session.
              removeToken();
              setJwtToken(null);
              setUserRole(null);
              setUserRestaurantId(null);
              setCurrentUser(null);
            });
        } else {
          removeToken();
        }
      } catch {
        removeToken();
      }
    }
  }, []);

  const handleLoginSuccess = (user: any, token: string) => {
    setCurrentUser(user);
    setJwtToken(token);
    setUserRole(user.role);
    setUserRestaurantId(user.restaurantId);
    // Refresh role-scoped data
    if (user.role !== 'ROLE_CUSTOMER' && user.restaurantId) {
      setCurrentRestaurantId(user.restaurantId);
    }
    refreshOrders();
    // If they were mid-checkout, drop them straight into payment now that they're in.
    if (pendingCheckout) {
      setPendingCheckout(false);
      setIsPaymentModalOpen(true);
    }
    // For customers, show the restaurant selector if they have memberships
    // (the selector handles the case of zero memberships by showing available restaurants to join)
    if (user.role === 'ROLE_CUSTOMER') {
      setIsRestaurantSelectorOpen(true);
    }
  };

  // Handle restaurant selection from the RestaurantSelector
  const handleRestaurantSelected = (restaurantId: string, newToken: string) => {
    // Store the new restaurant-scoped JWT
    storeAuthToken(newToken);
    setJwtToken(newToken);
    setUserRestaurantId(restaurantId);
    setCurrentRestaurantId(restaurantId);
    setIsRestaurantSelectorOpen(false);
    refreshOrders();
    // If they were mid-checkout, resume
    if (pendingCheckout) {
      setPendingCheckout(false);
      setIsPaymentModalOpen(true);
    }
  };

  // Handle skipping restaurant selection (browse as guest)
  const handleRestaurantSelectorSkip = () => {
    setIsRestaurantSelectorOpen(false);
    if (pendingCheckout) {
      setPendingCheckout(false);
      setIsPaymentModalOpen(true);
    }
  };

  const handleLogout = () => {
    setCurrentUser(null);
    setJwtToken(null);
    setUserRole(null);
    setUserRestaurantId(null);
    setPendingCheckout(false);
    setIsPaymentModalOpen(false); // never leave a checkout form open on a logged-out session
    removeToken();
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

  // Ordering is customer-only (guests may browse + build a cart, then sign in at
  // checkout). Staff accounts manage the kitchen and are blocked, and the API
  // rejects POST /orders for any non-customer role — including super admin, so
  // the UI must not offer checkout to them either.
  const canOrder = !userRole || userRole === 'ROLE_CUSTOMER';

  // Checkout requires a signed-in CUSTOMER with a VALID token. Staff accounts
  // manage the kitchen — they cannot place customer orders. Anonymous users (or
  // sessions whose token expired mid-browsing) are sent to the auth modal first
  // (their cart is kept), then resume checkout right after logging in.
  const handleCheckoutRequest = () => {
    if (!canOrder) {
      window.alert('Staff accounts cannot place customer orders. Ordering is available for customer accounts only.');
      return;
    }
    const expiry = getTokenExpiryTime();
    const sessionValid = jwtToken !== null && expiry !== null && expiry.getTime() > Date.now();
    if (!sessionValid) {
      if (jwtToken) {
        // Expired token — clear both storage AND React state so nothing (SSE,
        // role-scoped views) keeps treating the user as logged in.
        removeToken();
        setJwtToken(null);
        setUserRole(null);
        setUserRestaurantId(null);
        setCurrentUser(null);
      }
      setPendingCheckout(true);
      setIsAuthModalOpen(true);
      return;
    }
    setIsPaymentModalOpen(true);
  };

  const handlePaymentSuccess = (newOrder: Order) => {
    setActiveTab('orders');
    refreshOrders();
  };

  // Determine which tabs to show based on role
  const isSuperAdmin = userRole === 'ROLE_SUPER_ADMIN';
  const isAdmin = hasRole(userRole, 'ROLE_ADMIN');
  const isCustomer = !userRole || userRole === 'ROLE_CUSTOMER';
  // Menu/price management is manager+ only; chefs are kitchen-only.
  const canManageMenu = canManage(userRole);

  // Resolve the restaurant context for staff views
  const staffRestaurantId = userRestaurantId || currentRestaurantId;

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
          if (cart.length > 0) handleCheckoutRequest();
        }}
        currentUser={currentUser}
        onOpenAuthModal={() => setIsAuthModalOpen(true)}
        // Multi-tenant additions
        userRole={userRole}
        restaurantPicker={
          isCustomer || isSuperAdmin ? (
            <RestaurantPicker
              restaurants={restaurants}
              currentRestaurantId={currentRestaurantId}
              onSelect={(id) => {
                setCurrentRestaurantId(id);
                setCart([]);
              }}
            />
          ) : undefined
        }
        memberCount={memberCount}
        notificationsBell={<NotificationsBell liveNotifications={liveNotifications} />}
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
            onProceedToPayment={handleCheckoutRequest}
            allowOrdering={canOrder}
            onOpenAuth={() => setIsAuthModalOpen(true)}
            restaurantId={currentRestaurantId || undefined}
            onPreOrderDateChange={setPreOrderDate}
          />
        )}

        {activeTab === 'menu_management' && staffRestaurantId && canManageMenu && (
          <MenuManagement restaurantId={staffRestaurantId} canManage={canManageMenu} />
        )}

        {activeTab === 'orders' && (
          isCustomer ? (
            <OrderTracking
              liveUpdate={(handler) => {
                orderTrackingRefreshRef.current = handler;
              }}
            />
          ) : (
            <PreBookingsDashboard orders={orders} userRole={userRole} />
          )
        )}

        {activeTab === 'chef_prep' && (
          staffRestaurantId ? (
            <IngredientPlanning restaurantId={staffRestaurantId} canManage={canManageMenu} />
          ) : (
            <ChefPrepSummary prepItems={INITIAL_PREP_ITEMS} />
          )
        )}

        {activeTab === 'dashboard' && (
          <ManagerDashboard
            userRole={userRole}
            restaurantId={currentRestaurantId}
          />
        )}

        {activeTab === 'spring_backend' && <BackendInspectorModal />}

        {activeTab === 'super_admin' && (
          <SuperAdminDashboard
            onManageRestaurant={(id) => {
              // Super admin picks a restaurant → manage it like its own admin.
              setCurrentRestaurantId(id);
              setActiveTab('menu_management');
            }}
          />
        )}

        {activeTab === 'staff_management' && staffRestaurantId && (
          <StaffManagement
            restaurantId={staffRestaurantId}
            restaurantName={restaurants.find((r) => r.id === staffRestaurantId)?.name}
          />
        )}

        {activeTab === 'customer_memberships' && staffRestaurantId && (
          <CustomerMembershipManager
            restaurantId={staffRestaurantId}
            restaurantName={restaurants.find((r) => r.id === staffRestaurantId)?.name}
            onMembersChanged={() => {
              // Re-fetch member count so the badge updates immediately
              const role = getTokenRole();
              if ((role === 'ROLE_ADMIN' || role === 'ROLE_SUPER_ADMIN') && staffRestaurantId) {
                listCustomerMembers(staffRestaurantId)
                  .then((m) => setMemberCount(m.length))
                  .catch(() => {});
              }
            }}
          />
        )}

        {activeTab === 'ingredients' && staffRestaurantId && (
          <IngredientPlanning restaurantId={staffRestaurantId} canManage={canManageMenu} />
        )}

        {activeTab === 'preorder_settings' && staffRestaurantId && canManageMenu && (
          <PreOrderSettings restaurantId={staffRestaurantId} />
        )}
      </main>

      {/* Auth Modal */}
      <AuthModal
        isOpen={isAuthModalOpen}
        onClose={() => {
          setIsAuthModalOpen(false);
          setPendingCheckout(false);
        }}
        currentUser={currentUser}
        onLoginSuccess={handleLoginSuccess}
        onLogout={handleLogout}
        promptMessage={pendingCheckout ? 'Please sign in to continue with your order.' : undefined}
      />

      {/* Restaurant Selector (post-login for customers) */}
      <RestaurantSelector
        isOpen={isRestaurantSelectorOpen}
        onSelect={handleRestaurantSelected}
        onSkip={handleRestaurantSelectorSkip}
        onLogout={() => {
          setIsRestaurantSelectorOpen(false);
          handleLogout();
        }}
        username={currentUser?.username || ''}
      />

      {/* Payment Modal */}
      <RealtimePaymentModal
        isOpen={isPaymentModalOpen}
        onClose={() => setIsPaymentModalOpen(false)}
        cart={cart}
        clearCart={clearCart}
        onPaymentSuccess={handlePaymentSuccess}
        currentUser={currentUser}
        restaurantId={currentRestaurantId || undefined}
        restaurantName={restaurants.find((r) => r.id === currentRestaurantId)?.name}
        initialPreOrderDate={preOrderDate}
      />

      {/* Bottom Navigation */}
      <BottomNav
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        userRole={userRole}
        memberCount={memberCount}
      />
    </div>
  );
}