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
import { AvailabilityWarningModal } from './components/AvailabilityWarningModal';
import { AuthModal } from './components/AuthModal';
import { SuperAdminDashboard } from './components/SuperAdminDashboard';
import { StaffManagement } from './components/StaffManagement';
import { CustomerMembershipManager } from './components/CustomerMembershipManager';
import { IngredientPlanning } from './components/IngredientPlanning';
import { KitchenStockDashboard, LowStockAlert } from './components/KitchenStockDashboard';
import { ManagerDashboard } from './components/ManagerDashboard';
import { AdminDashboard } from './components/AdminDashboard';
import { PreOrderSettings } from './components/PreOrderSettings';
import { OrderTracking } from './components/OrderTracking';
import { NotificationsBell } from './components/NotificationsBell';
import { RestaurantPicker } from './components/RestaurantPicker';
import { RestaurantSelector } from './components/RestaurantSelector';
import { getToken, storeToken as storeAuthToken, removeToken, getTokenRole, getTokenRestaurantId, getTokenUserId, getTokenExpiryTime } from './lib/tokenManager';
import { listRestaurants, getPublicMenu, getRestaurantOrders, getMyOrders, getCurrentUser, listCustomerMembers, getMyRestaurants, selectRestaurant } from './lib/apiClient';
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
  const [showAvailabilityWarning, setShowAvailabilityWarning] = useState(false);
  const [unavailableCartItems, setUnavailableCartItems] = useState<CartItem[]>([]);
  const [availableCartItems, setAvailableCartItems] = useState<CartItem[]>([]);

  // Real-time availability toast
  const [availabilityToast, setAvailabilityToast] = useState<{ title: string; message: string; type: 'info' | 'warning' } | null>(null);

  // Real-time plate count updates from SSE — maps menuItemId → remaining plates
  const [plateUpdates, setPlateUpdates] = useState<Map<string, number>>(new Map());

  // Real-time table availability updates from SSE
  const [tableAvailabilityUpdate, setTableAvailabilityUpdate] = useState<any>(null);

  // Real-time low-stock ingredient alerts from SSE
  const [lowStockAlerts, setLowStockAlerts] = useState<LowStockAlert[]>([]);

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

  // Realtime SSE: handle incoming notifications and menu availability changes
  // OrderTracking registers a refresh callback here; realtime order events
  // invoke it so the customer's order list updates live (the SSE payload only
  // carries orderId/type, so the list is re-fetched rather than patched).
  const orderTrackingRefreshRef = useRef<(() => void) | null>(null);

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

  // Realtime SSE: handle incoming notifications and menu availability changes
  // Uses refs for refreshOrders/orderTrackingRefreshRef to avoid stale closures
  // while keeping the callback stable for the SSE hook.
  const refreshOrdersRef = useRef(refreshOrders);
  refreshOrdersRef.current = refreshOrders;

  const handleRealtimeEvent = useCallback((event: { type: string; data: any }) => {
    const { type, data } = event;

    if (type === 'notification' && data?.id) {
      setLiveNotifications((prev) => [...prev, data as Notification]);
      // If the event contains order status update, refresh orders
      if (data?.type === 'ORDER_STATUS' || data?.type === 'ORDER_READY' || data?.type === 'NEW_ORDER') {
        refreshOrdersRef.current();
        orderTrackingRefreshRef.current?.();
      }
    }

    if (type === 'menu_availability' && data) {
      // Update the menu items state to reflect the new status and plate count
      if (data.menuItemId && data.status) {
        setMenuItems((prev) =>
          prev.map((item) =>
            item.id === data.menuItemId
              ? { ...item, status: data.status, dailyPlateCount: data.dailyPlateCount ?? item.dailyPlateCount }
              : item
          )
        );

        // Update real-time plate counts for the menu view
        if (data.remainingPlates !== undefined && data.remainingPlates !== null) {
          setPlateUpdates((prev) => {
            const next = new Map(prev);
            next.set(data.menuItemId, data.remainingPlates);
            return next;
          });
        }

        // Show a toast notification
        const isSoldOut = data.status === 'Sold Out';
        setAvailabilityToast({
          title: data.title || 'Menu Updated',
          message: isSoldOut
            ? `${data.title} is now sold out`
            : `${data.title} is now available again`,
          type: isSoldOut ? 'warning' : 'info',
        });
        // Auto-dismiss after 5 seconds
        setTimeout(() => setAvailabilityToast(null), 5000);
      }
    }

    if (type === 'table_availability' && data) {
      setTableAvailabilityUpdate(data);
    }

    if (type === 'ingredient_low_stock' && data) {
      const alert: LowStockAlert = {
        orderId: data.orderId,
        orderNumber: data.orderNumber,
        lowStockIngredients: data.lowStockIngredients || [],
        message: data.message || '',
        timestamp: new Date().toISOString(),
      };
      setLowStockAlerts((prev) => [alert, ...prev].slice(0, 10));
    }
  }, []);

  useRealtimeNotifications(handleRealtimeEvent, !!jwtToken);

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
          // Default role-specific landing tab on session restore
          if (payload.role === 'ROLE_SUPER_ADMIN') {
            setActiveTab('super_admin');
          } else if (payload.role === 'ROLE_ADMIN' && payload.restaurantId) {
            setActiveTab('admin_dashboard');
          }
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
    // Default admin to their admin dashboard
    if (user.role === 'ROLE_ADMIN' && user.restaurantId) {
      setActiveTab('admin_dashboard');
    }
    // Default super admin to the platform overview
    if (user.role === 'ROLE_SUPER_ADMIN') {
      setActiveTab('super_admin');
    }
    refreshOrders();
    // If they were mid-checkout, drop them straight into payment now that they're in.
    if (pendingCheckout) {
      setPendingCheckout(false);
      setIsPaymentModalOpen(true);
    }
    // For customers, show the restaurant selector only if they have multiple
    // memberships or zero memberships. Single-membership users are auto-selected.
    if (user.role === 'ROLE_CUSTOMER') {
      getMyRestaurants()
        .then((memberships) => {
          if (memberships.length !== 1) {
            setIsRestaurantSelectorOpen(true);
          } else if (memberships.length === 1 && memberships[0].restaurantId) {
            // Auto-select the single restaurant
            selectRestaurant(memberships[0].restaurantId)
              .then((resp) => {
                if (resp?.token) {
                  storeAuthToken(resp.token);
                  setJwtToken(resp.token);
                  setUserRestaurantId(memberships[0].restaurantId);
                  setCurrentRestaurantId(memberships[0].restaurantId);
                }
              })
              .catch(() => {});
          }
        })
        .catch(() => {
          // If memberships can't be loaded, show the selector anyway
          setIsRestaurantSelectorOpen(true);
        });
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

  const addToCart = (item: MenuItem, maxQuantity?: number | null) => {
    setCart((prev) => {
      const existingIndex = prev.findIndex((ci) => ci.menuItem.id === item.id);
      if (existingIndex > -1) {
        const currentQty = prev[existingIndex].quantity;
        if (maxQuantity !== null && maxQuantity !== undefined && currentQty >= maxQuantity) {
          return prev; // Don't add — plate limit reached
        }
        const updated = [...prev];
        updated[existingIndex].quantity += 1;
        return updated;
      }
      if (maxQuantity !== null && maxQuantity !== undefined && maxQuantity <= 0) {
        return prev; // No plates left
      }
      return [...prev, { menuItem: item, quantity: 1 }];
    });
  };

  const removeFromCart = (itemId: string) => {
    setCart((prev) => prev.filter((ci) => ci.menuItem.id !== itemId));
  };

  const clearCart = () => setCart([]);
  const totalCartCount = cart.reduce((sum, ci) => sum + ci.quantity, 0);

  /**
   * Order Again: adds previous order items to the cart.
   * Uses current menu data for availability and price validation.
   * If a menu item is no longer available, it's skipped with a warning.
   */
  const handleReorder = (reorderItems: { menuItemId: string; title: string; price: number; quantity: number }[], restaurantId: string) => {
    // Switch to restaurant if needed
    if (restaurantId && restaurantId !== userRestaurantId) {
      setUserRestaurantId(restaurantId);
    }
    // Switch to customer menu tab
    setActiveTab('customer_menu');
    // Clear existing cart
    setCart([]);
    // Add items — use menuItems from current restaurant if available
    const newCart: CartItem[] = [];
    for (const ri of reorderItems) {
      const menuItem = menuItems.find(m => m.id === ri.menuItemId);
      if (menuItem && menuItem.status === 'Available') {
        newCart.push({ menuItem, quantity: ri.quantity });
      } else {            // Item not found or sold out — skip silently (checkout will validate)
        // Use a synthetic MenuItem with the old price as fallback
        newCart.push({
          menuItem: {
            id: ri.menuItemId,
            title: ri.title,
            price: ri.price,
            restaurantId,
            status: 'Available',
            category: 'Mains',
            isVeg: true,
            spiceLevel: 'Medium',
            description: '',
            imageUrl: '',
          },
          quantity: ri.quantity,
        });
      }
    }
    setCart(newCart);
  };

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

    // Check cart items against current menu availability before proceeding
    const unavailable: CartItem[] = [];
    const available: CartItem[] = [];
    cart.forEach((ci) => {
      const current = menuItems.find((m) => m.id === ci.menuItem.id);
      if (current && current.status === 'Sold Out') {
        unavailable.push(ci);
      } else {
        available.push(ci);
      }
    });

    if (unavailable.length > 0) {
      setUnavailableCartItems(unavailable);
      setAvailableCartItems(available);
      setShowAvailabilityWarning(true);
      return;
    }

    setIsPaymentModalOpen(true);
  };

  /** Remove a single unavailable item from the warning modal. */
  const handleRemoveUnavailableItem = (menuItemId: string) => {
    removeFromCart(menuItemId);
    setUnavailableCartItems((prev) => prev.filter((ci) => ci.menuItem.id !== menuItemId));
    setAvailableCartItems((prev) => {
      const found = cart.find((ci) => ci.menuItem.id === menuItemId);
      return found ? [...prev, found] : prev;
    });
  };

  /** Remove all unavailable items and proceed with available ones. */
  const handleRemoveAllUnavailable = () => {
    unavailableCartItems.forEach((ci) => removeFromCart(ci.menuItem.id));
    setUnavailableCartItems([]);
    // All remaining items in the cart are available — proceed
    setShowAvailabilityWarning(false);
    setIsPaymentModalOpen(true);
  };

  /** Proceed with only the available items (keep them, skip unavailable). */
  const handleProceedWithAvailable = () => {
    unavailableCartItems.forEach((ci) => removeFromCart(ci.menuItem.id));
    setUnavailableCartItems([]);
    setShowAvailabilityWarning(false);
    setIsPaymentModalOpen(true);
  };

  /** Go back to the menu to adjust the cart. */
  const handleGoBackToMenu = () => {
    setShowAvailabilityWarning(false);
    setUnavailableCartItems([]);
    setAvailableCartItems([]);
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
            plateUpdates={plateUpdates}
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
              onReorder={handleReorder}
            />
          ) : (
            <PreBookingsDashboard orders={orders} userRole={userRole} refreshOrders={refreshOrders} />
          )
        )}

        {activeTab === 'chef_prep' && (
          staffRestaurantId ? (
            <div className="flex flex-col gap-6 max-w-[1440px] mx-auto px-4 md:px-8 pt-6 pb-28">
              <KitchenStockDashboard
                restaurantId={staffRestaurantId}
                lowStockAlerts={lowStockAlerts}
              />
              <IngredientPlanning restaurantId={staffRestaurantId} canManage={canManageMenu} />
            </div>
          ) : (
            <ChefPrepSummary prepItems={INITIAL_PREP_ITEMS} />
          )
        )}

        {activeTab === 'admin_dashboard' && (
          <AdminDashboard
            restaurantId={currentRestaurantId}
            restaurantName={restaurants.find((r) => r.id === currentRestaurantId)?.name}
            restaurant={restaurants.find((r) => r.id === currentRestaurantId)}
            onNavigate={(tab) => setActiveTab(tab as ViewTab)}
          />
        )}

        {activeTab === 'dashboard' && (
          <ManagerDashboard
            userRole={userRole}
            restaurantId={currentRestaurantId}
            onNavigate={(tab) => setActiveTab(tab as ViewTab)}
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

      {/* Availability Warning Modal */}
      <AvailabilityWarningModal
        isOpen={showAvailabilityWarning}
        unavailableItems={unavailableCartItems}
        availableItems={availableCartItems}
        onRemoveUnavailable={handleRemoveUnavailableItem}
        onRemoveAllUnavailable={handleRemoveAllUnavailable}
        onProceedWithAvailable={handleProceedWithAvailable}
        onGoBackToMenu={handleGoBackToMenu}
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
        tableAvailabilityUpdate={tableAvailabilityUpdate}
      />

      {/* Bottom Navigation */}
      <BottomNav
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        userRole={userRole}
        memberCount={memberCount}
      />

      {/* Real-time availability toast */}
      {availabilityToast && (
        <div className="fixed top-4 right-4 z-50 animate-slide-in">
          <div
            className={`flex items-center gap-3 px-4 py-3 rounded-xl border shadow-2xl backdrop-blur-xl max-w-sm ${
              availabilityToast.type === 'warning'
                ? 'bg-rose-950/90 border-rose-800/50 text-rose-200'
                : 'bg-emerald-950/90 border-emerald-800/50 text-emerald-200'
            }`}
          >
            <div className="shrink-0">
              {availabilityToast.type === 'warning' ? (
                <svg className="w-5 h-5 text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z" />
                </svg>
              ) : (
                <svg className="w-5 h-5 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              )}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-bold truncate">{availabilityToast.title}</p>
              <p className="text-[10px] opacity-80 truncate">{availabilityToast.message}</p>
            </div>
            <button
              onClick={() => setAvailabilityToast(null)}
              className="shrink-0 opacity-60 hover:opacity-100 cursor-pointer"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}