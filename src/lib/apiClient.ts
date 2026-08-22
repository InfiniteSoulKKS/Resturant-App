import { authenticatedFetch } from './tokenManager';

/**
 * API Service for SavoryStay Spring Boot Backend
 * Handles all authentication and OTP-related API calls
 */

const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const API_ENDPOINT = `${API_BASE_URL}/api/v1/auth`;

export interface AuthResponse {
  success: boolean;
  token: string;
  user: {
    id: string;
    username: string;
    email: string;
    phone?: string;
    role: 'ROLE_CUSTOMER' | 'ROLE_CHEF' | 'ROLE_MANAGER' | 'ROLE_ADMIN' | 'ROLE_SUPER_ADMIN';
    restaurantId?: string;
    enabled: boolean;
    createdAt: string;
    lastLogin?: string;
  };
  message?: string;
}

export interface OtpResponse {
  success: boolean;
  message: string;
  otpId?: number;
  expiresIn?: string;
  /** Present when the backend runs in demo mode (no SMS/email provider configured). */
  demoOtp?: string;
  demoMode?: boolean;
}

export interface OtpVerifyResponse {
  success: boolean;
  message: string;
  verified: boolean;
}

/**
 * Register user with phone
 */
export async function registerUser(payload: {
  username: string;
  email: string;
  password: string;
  phone?: string;
  otpCode?: string;
  otpChannel?: 'EMAIL' | 'SMS' | 'WHATSAPP';
}): Promise<AuthResponse> {
  const response = await fetch(`${API_ENDPOINT}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'Registration failed');
  }

  return data;
}

/**
 * Traditional password-based login
 */
export async function loginWithPassword(payload: {
  username: string;
  password: string;
}): Promise<AuthResponse> {
  const response = await fetch(`${API_ENDPOINT}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'Login failed');
  }

  return data;
}

/**
 * Send OTP via Email.
 * Pass `username` in login flows so the backend only issues OTPs to accounts
 * that exist (registration sends omit it).
 */
export async function sendOtpEmail(email: string, username?: string): Promise<OtpResponse> {
  const response = await fetch(`${API_ENDPOINT}/otp/send/email`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, ...(username ? { username } : {}) }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'Failed to send email OTP');
  }

  return data;
}

/**
 * Send OTP via SMS.
 * Pass `username` in login flows so the backend only issues OTPs to accounts
 * that exist (registration sends omit it).
 */
export async function sendOtpSms(phone: string, username?: string): Promise<OtpResponse> {
  const response = await fetch(`${API_ENDPOINT}/otp/send/sms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, ...(username ? { username } : {}) }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'Failed to send SMS OTP');
  }

  return data;
}

/**
 * Send OTP via WhatsApp.
 * Pass `username` in login flows so the backend only issues OTPs to accounts
 * that exist (registration sends omit it).
 */
export async function sendOtpWhatsApp(phone: string, username?: string): Promise<OtpResponse> {
  const response = await fetch(`${API_ENDPOINT}/otp/send/whatsapp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phone, ...(username ? { username } : {}) }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'Failed to send WhatsApp OTP');
  }

  return data;
}

/**
 * Verify OTP code
 */
export async function verifyOtp(payload: {
  userId: string;
  otpCode: string;
  channel: 'EMAIL' | 'SMS' | 'WHATSAPP';
}): Promise<OtpVerifyResponse> {
  const response = await fetch(`${API_ENDPOINT}/otp/verify`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'OTP verification failed');
  }

  return data;
}

/**
 * Resend OTP
 */
export async function resendOtp(payload: {
  userId: string;
  channel: 'EMAIL' | 'SMS' | 'WHATSAPP';
}): Promise<OtpResponse> {
  const response = await fetch(`${API_ENDPOINT}/otp/resend`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'Failed to resend OTP');
  }

  return data;
}

/**
 * Login with OTP verification
 */
export async function loginWithOtp(payload: {
  username: string;
  otpCode: string;
  channel: 'EMAIL' | 'SMS' | 'WHATSAPP';
  deliveryTarget?: string;
}): Promise<AuthResponse> {
  const response = await fetch(`${API_ENDPOINT}/login-with-otp`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.message || 'OTP login failed');
  }

  return data;
}

/**
 * Pre-registration availability check — warns the sign-up form early if a
 * username/email/phone is already taken, before the user verifies an OTP.
 * Advisory only; /register remains authoritative.
 */
export async function checkAvailability(payload: {
  username?: string;
  email?: string;
  phone?: string;
}): Promise<{ usernameTaken: boolean; emailTaken: boolean; phoneTaken: boolean }> {
  const params = new URLSearchParams();
  if (payload.username) params.set('username', payload.username);
  if (payload.email) params.set('email', payload.email);
  if (payload.phone) params.set('phone', payload.phone);
  const res = await fetch(`${API_BASE_URL}/api/v1/auth/check-availability?${params.toString()}`);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || 'Availability check failed');
  }
  return data;
}

/**
 * Fetch the authenticated user's profile from the JWT in the current session.
 * Used on page load to restore the full session (header, checkout prefill).
 */
export async function getCurrentUser(): Promise<UserProfile> {
  const res = await authenticatedFetch('/api/v1/auth/me');
  const data = await readJson<{ user: any }>(res);
  return data.user as UserProfile;
}

/**
 * Check if the backend is reachable via the real health endpoint.
 */
export async function checkApiHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/health`);
    return response.ok;
  } catch {
    return false;
  }
}

// ============================================================================
// MULTI-RESTAURANT PLATFORM APIs
// ============================================================================

import {
  MenuItem,
  Order,
  Restaurant,
  Ingredient,
  IngredientForecast,
  IngredientForecastResponse,
  DishForecast,
  Notification,
  UserProfile,
  OperatingHour,
  PreOrderSettings,
  DishAvailabilityView,
  PreOrderDateOption,
  RecipeIngredient,
} from '../types';

async function readJson<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || 'Request failed');
  }
  return data;
}

/** Parse a backend entity into the frontend shape (price strings → numbers). */
export function parseMenuItem(m: any): MenuItem {
  return { ...m, price: Number(m.price), restaurantId: m.restaurantId || '' };
}

export function parseOrder(o: any): Order {
  return {
    ...o,
    totalAmount: Number(o.totalAmount),
    timestamp: o.createdAt ? new Date(o.createdAt).getTime() : Date.now(),
    items: (o.items || []).map((item: any) => ({
      ...item,
      // Backend DTO sends "unitPrice" — map to "price" so frontend OrderItemSummary works
      price: item.price ?? Number(item.unitPrice ?? 0),
      quantity: Number(item.quantity),
    })),
  };
}

export function parseRestaurant(r: any): Restaurant {
  return { ...r };
}

// ==================== RESTAURANTS ====================

export async function listRestaurants(): Promise<Restaurant[]> {
  const res = await fetch(`${API_BASE_URL}/api/v1/restaurants`);
  const data = await readJson<{ success: boolean; restaurants: any[] }>(res);
  return (data.restaurants || []).map(parseRestaurant);
}

export async function getRestaurant(id: string): Promise<Restaurant> {
  const res = await fetch(`${API_BASE_URL}/api/v1/restaurants/${id}`);
  const data = await readJson<{ success: boolean; restaurant: any }>(res);
  return parseRestaurant(data.restaurant);
}

// ==================== PUBLIC MENU ====================

export async function getPublicMenu(restaurantId: string): Promise<MenuItem[]> {
  const res = await fetch(`${API_BASE_URL}/api/v1/restaurants/${restaurantId}/menu`);
  const data = await readJson<{ success: boolean; menuItems: any[] }>(res);
  return (data.menuItems || []).map(parseMenuItem);
}

// ==================== RESTAURANT SETTINGS (tables & time slots) ====================

export interface RestaurantSettings {
  restaurantId: string;
  tableConfig: string; // JSON array of {type, count}
  tableTypes: { type: string; count: number }[];
  totalTables: number;
  pickupTimeSlots: string[];
  dineinTimeSlots: string[];
}

export async function getRestaurantSettings(restaurantId: string): Promise<RestaurantSettings> {
  const res = await fetch(`${API_BASE_URL}/api/v1/restaurants/${restaurantId}/settings`);
  const data = await readJson<{ success: boolean; settings: RestaurantSettings }>(res);
  return data.settings;
}

export interface TableAvailability {
  type: string;
  total: number;
  booked: number;
  remaining: number;
}

export async function getTableAvailability(
  restaurantId: string, date: string, timeSlot: string
): Promise<TableAvailability[]> {
  const res = await fetch(
    `${API_BASE_URL}/api/v1/restaurants/${restaurantId}/table-availability?date=${date}&timeSlot=${encodeURIComponent(timeSlot)}`
  );
  const data = await readJson<{ success: boolean; tables: TableAvailability[] }>(res);
  return data.tables;
}

export interface PlateAvailabilityItem {
  menuItemId: string;
  title: string;
  dailyPlateCount: number | null;
  platesOrdered: number;
  remaining: number; // -1 = unlimited
  available: boolean;
}

export async function getPlateAvailability(
  restaurantId: string, date?: string
): Promise<PlateAvailabilityItem[]> {
  const qs = date ? `?date=${date}` : '';
  const res = await fetch(`${API_BASE_URL}/api/v1/restaurants/${restaurantId}/plate-availability${qs}`);
  const data = await readJson<{ success: boolean; items: PlateAvailabilityItem[] }>(res);
  return data.items;
}

// ==================== SUPER ADMIN ====================

export async function superAdminCreateRestaurant(payload: {
  name: string;
  description?: string;
  address?: string;
  city?: string;
  cuisine?: string;
  phone?: string;
  email?: string;
  logoUrl?: string;
  currency?: string;
  adminUsername: string;
  adminEmail: string;
  adminPassword: string;
}): Promise<Restaurant> {
  const res = await authenticatedFetch('/api/v1/super-admin/restaurants', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; restaurant: any }>(res);
  return parseRestaurant(data.restaurant);
}

export async function superAdminListRestaurants(): Promise<Restaurant[]> {
  const res = await authenticatedFetch('/api/v1/super-admin/restaurants');
  const data = await readJson<{ success: boolean; restaurants: any[] }>(res);
  return (data.restaurants || []).map(parseRestaurant);
}

export async function superAdminUpdateRestaurant(id: string, updates: Partial<Restaurant>): Promise<Restaurant> {
  const res = await authenticatedFetch(`/api/v1/super-admin/restaurants/${id}`, {
    method: 'PUT',
    body: JSON.stringify(updates),
  });
  const data = await readJson<{ success: boolean; restaurant: any }>(res);
  return parseRestaurant(data.restaurant);
}

export async function superAdminDeleteRestaurant(id: string): Promise<void> {
  const res = await authenticatedFetch(`/api/v1/super-admin/restaurants/${id}`, { method: 'DELETE' });
  await readJson(res);
}

// ==================== STAFF MANAGEMENT ====================

export async function addStaff(payload: {
  username: string;
  email: string;
  password: string;
  phone?: string;
  role: 'ROLE_MANAGER' | 'ROLE_CHEF';
  restaurantId?: string;
}): Promise<UserProfile> {
  const res = await authenticatedFetch('/api/v1/staff', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; staff: any }>(res);
  return data.staff;
}

export async function listStaff(restaurantId?: string): Promise<UserProfile[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/staff${q}`);
  const data = await readJson<{ success: boolean; staff: any[] }>(res);
  return data.staff || [];
}

export async function setStaffEnabled(staffId: string, enabled: boolean, restaurantId?: string): Promise<UserProfile> {
  const res = await authenticatedFetch(`/api/v1/staff/${staffId}`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled, ...(restaurantId ? { restaurantId } : {}) }),
  });
  const data = await readJson<{ success: boolean; staff: any }>(res);
  return data.staff;
}

// ==================== MENU (STAFF) ====================

export async function staffListMenu(restaurantId?: string): Promise<MenuItem[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/menu${q}`);
  const data = await readJson<{ success: boolean; menuItems: any[] }>(res);
  return (data.menuItems || []).map(parseMenuItem);
}

export async function staffCreateMenuItem(payload: any): Promise<MenuItem> {
  const res = await authenticatedFetch('/api/v1/menu', { method: 'POST', body: JSON.stringify(payload) });
  const data = await readJson<{ success: boolean; menuItem: any }>(res);
  return parseMenuItem(data.menuItem);
}

export async function staffUpdateMenuItem(id: string, payload: any): Promise<MenuItem> {
  const res = await authenticatedFetch(`/api/v1/menu/${id}`, { method: 'PUT', body: JSON.stringify(payload) });
  const data = await readJson<{ success: boolean; menuItem: any }>(res);
  return parseMenuItem(data.menuItem);
}

export async function staffDeleteMenuItem(id: string, restaurantId?: string): Promise<void> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/menu/${id}${q}`, { method: 'DELETE' });
  await readJson(res);
}

export async function getMenuItemIngredients(menuItemId: string): Promise<RecipeIngredient[]> {
  const res = await authenticatedFetch(`/api/v1/menu/${menuItemId}/ingredients`);
  const data = await readJson<{ success: boolean; ingredients: any[] }>(res);
  return (data.ingredients || []).map((i) => ({
    name: i.name,
    quantityPerUnit: Number(i.quantityPerUnit),
    unit: i.unit,
  }));
}

// ==================== CART AVAILABILITY CHECK ====================

export interface CartAvailabilityItem {
  menuItemId: string;
  title: string;
  status: string;
  quantity: number;
}

export interface CartAvailabilityResponse {
  allAvailable: boolean;
  unavailableItems: CartAvailabilityItem[];
}

/**
 * Check if all items in the cart are still available before checkout.
 * Returns a list of unavailable items (empty = all good).
 */
export async function checkCartAvailability(
  restaurantId: string,
  items: { menuItemId: string; quantity: number }[]
): Promise<CartAvailabilityResponse> {
  const res = await authenticatedFetch('/api/v1/menu/availability-check', {
    method: 'POST',
    body: JSON.stringify({ restaurantId, items }),
  });
  const data = await readJson<{ success: boolean; allAvailable: boolean; unavailableItems: any[] }>(res);
  return {
    allAvailable: data.allAvailable,
    unavailableItems: data.unavailableItems || [],
  };
}

// ==================== ORDERS ====================

export async function placeOrder(payload: {
  restaurantId: string;
  orderType: 'PICKUP' | 'DINE_IN' | 'PRE_ORDER';
  tableNumber?: number;
  guests?: number;
  timeSlot?: string;
  pickupTime?: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  paymentMethod: string;
  items: { menuItemId: string; quantity: number; notes?: string }[];
}): Promise<Order> {
  const res = await authenticatedFetch('/api/v1/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; order: any }>(res);
  return parseOrder(data.order);
}

export async function getMyOrders(): Promise<Order[]> {
  const res = await authenticatedFetch('/api/v1/orders/mine');
  const data = await readJson<{ success: boolean; orders: any[] }>(res);
  return (data.orders || []).map(parseOrder);
}

export async function getRestaurantOrders(restaurantId?: string): Promise<Order[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/orders${q}`);
  const data = await readJson<{ success: boolean; orders: any[] }>(res);
  return (data.orders || []).map(parseOrder);
}

export async function updateOrderStatus(orderId: string, status: string, restaurantId?: string, reason?: string): Promise<Order> {
  const res = await authenticatedFetch('/api/v1/orders/status', {
    method: 'POST',
    body: JSON.stringify({ orderId, status, restaurantId, ...(reason ? { reason } : {}) }),
  });
  const data = await readJson<{ success: boolean; order: any }>(res);
  return parseOrder(data.order);
}

/**
 * Server-authoritative payment confirmation.
 * The backend verifies the caller owns the order (or is restaurant staff) and that
 * the amount matches the order total before marking it PAID.
 */
export async function confirmOrderPayment(
  orderId: string,
  payload: { amount: number; gateway: string }
): Promise<Order> {
  const res = await authenticatedFetch(`/api/v1/orders/${orderId}/payment`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; order: any }>(res);
  return parseOrder(data.order);
}

/** Manager/cashier marks a CASH order as paid at the counter. */
export async function markCashPaid(orderId: string, restaurantId?: string): Promise<Order> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/orders/${orderId}/mark-paid${q}`, {
    method: 'POST',
  });
  const data = await readJson<{ success: boolean; order: any }>(res);
  return parseOrder(data.order);
}

// ==================== INGREDIENTS ====================

export async function listIngredients(restaurantId?: string): Promise<Ingredient[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/ingredients${q}`);
  const data = await readJson<{ success: boolean; ingredients: any[] }>(res);
  return (data.ingredients || []).map((i) => ({
    ...i,
    stockQuantity: Number(i.stockQuantity),
    reorderLevel: Number(i.reorderLevel),
  }));
}

export async function createIngredient(payload: Partial<Ingredient>, restaurantId?: string): Promise<Ingredient> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/ingredients${q}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; ingredient: any }>(res);
  return data.ingredient;
}

export async function updateIngredient(id: string, payload: Partial<Ingredient>, restaurantId?: string): Promise<Ingredient> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/ingredients/${id}${q}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; ingredient: any }>(res);
  return data.ingredient;
}

export async function deleteIngredient(id: string, restaurantId?: string): Promise<void> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/ingredients/${id}${q}`, { method: 'DELETE' });
  await readJson(res);
}

export async function deactivateIngredient(id: string, restaurantId?: string): Promise<Ingredient> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/ingredients/${id}/deactivate${q}`, { method: 'PATCH' });
  const data = await readJson<{ success: boolean; ingredient: any }>(res);
  return data.ingredient;
}

export async function reactivateIngredient(id: string, restaurantId?: string): Promise<Ingredient> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/ingredients/${id}/reactivate${q}`, { method: 'PATCH' });
  const data = await readJson<{ success: boolean; ingredient: any }>(res);
  return data.ingredient;
}

export async function searchIngredients(query: string, restaurantId?: string, includeInactive = false): Promise<Ingredient[]> {
  const params = new URLSearchParams();
  if (restaurantId) params.set('restaurantId', restaurantId);
  if (query) params.set('q', query);
  if (includeInactive) params.set('includeInactive', 'true');
  const qs = params.toString();
  const res = await authenticatedFetch(`/api/v1/ingredients${qs ? `?${qs}` : ''}`);
  const data = await readJson<{ success: boolean; ingredients: any[] }>(res);
  return (data.ingredients || []).map((i) => ({
    ...i,
    stockQuantity: Number(i.stockQuantity),
    reorderLevel: Number(i.reorderLevel),
  }));
}

export async function getIngredientUsage(id: string): Promise<number> {
  const res = await authenticatedFetch(`/api/v1/ingredients/${id}/usage`);
  const data = await readJson<{ success: boolean; usageCount: number }>(res);
  return data.usageCount || 0;
}

export async function findSimilarIngredients(name: string, restaurantId?: string): Promise<Ingredient[]> {
  const params = new URLSearchParams();
  params.set('name', name);
  if (restaurantId) params.set('restaurantId', restaurantId);
  const res = await authenticatedFetch(`/api/v1/ingredients/similar?${params.toString()}`);
  const data = await readJson<{ success: boolean; similarIngredients: any[] }>(res);
  return (data.similarIngredients || []).map((i) => ({
    ...i,
    stockQuantity: Number(i.stockQuantity),
    reorderLevel: Number(i.reorderLevel),
  }));
}

export async function getIngredientForecast(restaurantId?: string, date?: string): Promise<IngredientForecastResponse> {
  const params = new URLSearchParams();
  if (restaurantId) params.set('restaurantId', restaurantId);
  if (date) params.set('date', date);
  const qs = params.toString();
  const res = await authenticatedFetch(`/api/v1/ingredients/forecast${qs ? `?${qs}` : ''}`);
  const data = await readJson<{ success: boolean; ingredients: any[]; dishes?: any[] }>(res);
  return {
    ingredients: (data.ingredients || []).map((i) => ({
      name: i.name,
      unit: i.unit,
      requiredQuantity: Number(i.requiredQuantity),
      currentStock: Number(i.currentStock),
      shortfall: Number(i.shortfall),
      needPurchase: i.needPurchase,
      reorderLevel: Number(i.reorderLevel),
    })),
    dishes: (data.dishes || []).map((d): DishForecast => ({
      menuItemId: d.menuItemId,
      dish: d.dish,
      plates: d.plates,
      ingredients: (d.ingredients || []).map((ing: any) => ({
        name: ing.name,
        unit: ing.unit,
        requiredQuantity: Number(ing.requiredQuantity),
      })),
    })),
  };
}

// ==================== PRE-ORDERS (availability, hours, settings) ====================

export async function getPreOrderDates(payload: {
  restaurantId: string;
  menuItemIds: string[];
  daysAhead?: number;
}): Promise<PreOrderDateOption[]> {
  const res = await authenticatedFetch('/api/v1/pre-orders/dates', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; dates: any[] }>(res);
  return (data.dates || []).map((d) => ({
    date: d.date,
    weekday: d.weekday,
    openTime: d.openTime,
    closeTime: d.closeTime,
    orderable: d.orderable,
    reasons: d.reasons || [],
    dishes: d.dishes || [],
  }));
}

export async function getOperatingHours(restaurantId?: string): Promise<OperatingHour[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/config/hours${q}`);
  const data = await readJson<{ success: boolean; operatingHours: any[] }>(res);
  return (data.operatingHours || []).map((h) => ({
    ...h,
    closed: !!h.closed,
  }));
}

export async function upsertOperatingHour(
  payload: {
    dayOfWeek: number;
    openTime?: string;
    closeTime?: string;
    closed?: boolean;
  },
  restaurantId?: string
): Promise<OperatingHour> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/config/hours${q}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; operatingHour: any }>(res);
  return data.operatingHour;
}

export async function getPreOrderSettings(restaurantId?: string): Promise<PreOrderSettings> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/config/settings${q}`);
  const data = await readJson<{ success: boolean; settings: any }>(res);
  return data.settings;
}

export async function updatePreOrderSettings(
  payload: { cutoffTime: string; advanceDays: number },
  restaurantId?: string
): Promise<PreOrderSettings> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/config/settings${q}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
  const data = await readJson<{ success: boolean; settings: any }>(res);
  return data.settings;
}

export async function getDishAvailability(menuItemId: string, restaurantId?: string): Promise<DishAvailabilityView> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/menu-items/${menuItemId}/availability${q}`);
  const data = await readJson<{ success: boolean; availability: any }>(res);
  return data.availability;
}

export async function setDishAvailability(menuItemId: string, days: number[], restaurantId?: string): Promise<void> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/menu-items/${menuItemId}/availability${q}`, {
    method: 'PUT',
    body: JSON.stringify({ days }),
  });
  await readJson(res);
}

export async function upsertSlotOverride(menuItemId: string, date: string, action: 'OPEN' | 'CLOSE', restaurantId?: string): Promise<void> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/pre-orders/menu-items/${menuItemId}/slots${q}`, {
    method: 'PUT',
    body: JSON.stringify({ date, action }),
  });
  await readJson(res);
}

export async function clearSlotOverride(menuItemId: string, date: string, restaurantId?: string): Promise<void> {
  const q = `?date=${encodeURIComponent(date)}${restaurantId ? `&restaurantId=${encodeURIComponent(restaurantId)}` : ''}`;
  const res = await authenticatedFetch(
    `/api/v1/pre-orders/menu-items/${menuItemId}/slots${q}`,
    { method: 'DELETE' }
  );
  await readJson(res);
}

// ==================== DASHBOARD ====================

export interface DashboardSummary {
  today: string;
  totalOrders: number;
  revenue: number;
  pending: number;
  preparing: number;
  ready: number;
  completed: number;
  delayed: number;
  cashPaymentsPending: number;
  ingredientShortages: number;
  soldOutDishes: number;
  tomorrowPreOrders: number;
  tomorrowExpectedRevenue: number;
  tomorrowIngredientShortfalls: number;
}

export interface DashboardExceptions {
  paymentFailures: number;
  delayedOrders: number;
  cashPaymentsPending: number;
  refundsPending: number;
  ingredientShortages: number;
  soldOutDishes: number;
  newOrders: number;
  preparingOrders: number;
  readyOrders: number;
  delayedOrderDetails: any[];
}

export interface ShoppingListItem {
  name: string;
  unit: string;
  requiredQuantity: number;
  currentStock: number;
  shortfall: number;
}

export interface CashReconciliation {
  date: string;
  totalCashOrders: number;
  expectedCash: number;
  paidOrders: number;
  pendingOrders: number;
  pendingAmount: number;
}

export interface PaymentReconciliation {
  date: string;
  gross: number;
  refunds: number;
  net: number;
  byMethod: Record<string, number>;
  countByMethod: Record<string, number>;
  pendingPayments: number;
  failedPayments: number;
  cashPending: number;
}

export interface KitchenProductionItem {
  menuItemId: string;
  dishName: string;
  requiredPlates: number;
  preparedPlates: number;
  remainingPlates: number;
  urgency: 'NORMAL' | 'DUE_SOON' | 'OVERDUE';
  earliestPickup?: string;
  orderNumbers: string[];
}

export interface DelayedOrder {
  orderId: string;
  orderNumber: string;
  customerName: string;
  orderStatus: string;
  pickupTime: string;
  delayMinutes: number;
  isDelayed: boolean;
}

export async function getDashboardSummary(restaurantId?: string): Promise<DashboardSummary> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/dashboard/summary${q}`);
  const data = await readJson<{ success: boolean; summary: DashboardSummary }>(res);
  return data.summary;
}

export async function getDashboardExceptions(restaurantId?: string): Promise<DashboardExceptions> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/dashboard/exceptions${q}`);
  const data = await readJson<{ success: boolean; exceptions: DashboardExceptions }>(res);
  return data.exceptions;
}

export async function getShoppingList(restaurantId?: string, date?: string): Promise<ShoppingListItem[]> {
  const params = new URLSearchParams();
  if (restaurantId) params.set('restaurantId', restaurantId);
  if (date) params.set('date', date);
  const qs = params.toString();
  const res = await authenticatedFetch(`/api/v1/dashboard/shopping-list${qs ? `?${qs}` : ''}`);
  const data = await readJson<{ success: boolean; shoppingList: ShoppingListItem[] }>(res);
  return data.shoppingList || [];
}

export async function getCashReconciliation(restaurantId?: string, date?: string): Promise<CashReconciliation> {
  const params = new URLSearchParams();
  if (restaurantId) params.set('restaurantId', restaurantId);
  if (date) params.set('date', date);
  const qs = params.toString();
  const res = await authenticatedFetch(`/api/v1/dashboard/cash-reconciliation${qs ? `?${qs}` : ''}`);
  const data = await readJson<{ success: boolean; reconciliation: CashReconciliation }>(res);
  return data.reconciliation;
}

export async function getPaymentReconciliation(restaurantId?: string, date?: string): Promise<PaymentReconciliation> {
  const params = new URLSearchParams();
  if (restaurantId) params.set('restaurantId', restaurantId);
  if (date) params.set('date', date);
  const qs = params.toString();
  const res = await authenticatedFetch(`/api/v1/dashboard/payment-reconciliation${qs ? `?${qs}` : ''}`);
  const data = await readJson<{ success: boolean; reconciliation: PaymentReconciliation }>(res);
  return data.reconciliation;
}

export async function getKitchenProduction(restaurantId?: string): Promise<KitchenProductionItem[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/orders/kitchen/production${q}`);
  const data = await readJson<{ success: boolean; production: KitchenProductionItem[] }>(res);
  return data.production || [];
}

export async function getDelayedOrders(restaurantId?: string): Promise<DelayedOrder[]> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/orders/kitchen/delayed${q}`);
  const data = await readJson<{ success: boolean; delayedOrders: DelayedOrder[] }>(res);
  return data.delayedOrders || [];
}

export async function toggleSoldOut(menuItemId: string, soldOut: boolean, restaurantId?: string): Promise<any> {
  const q = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/menu/${menuItemId}/sold-out${q}`, {
    method: 'POST',
    body: JSON.stringify({ soldOut }),
  });
  return readJson(res);
}

export async function cancelOrder(orderId: string, reason?: string): Promise<Order> {
  const res = await authenticatedFetch(`/api/v1/orders/${orderId}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason || 'Cancelled' }),
  });
  const data = await readJson<{ success: boolean; order: any }>(res);
  return parseOrder(data.order);
}

export async function initiateRefund(orderId: string, reason?: string): Promise<any> {
  const res = await authenticatedFetch(`/api/v1/orders/${orderId}/refund`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason || 'Refund requested' }),
  });
  return readJson(res);
}

export async function getOrderAudit(orderId: string): Promise<any[]> {
  const res = await authenticatedFetch(`/api/v1/orders/${orderId}/audit`);
  const data = await readJson<{ success: boolean; audit: any[] }>(res);
  return data.audit || [];
}

// ==================== NOTIFICATIONS ====================

export async function getMyNotifications(): Promise<{ notifications: Notification[]; unread: number }> {
  const res = await authenticatedFetch('/api/v1/notifications');
  const data = await readJson<{ success: boolean; notifications: any[]; unread: number }>(res);
  return { notifications: data.notifications || [], unread: data.unread || 0 };
}

export async function markNotificationsRead(): Promise<void> {
  const res = await authenticatedFetch('/api/v1/notifications/read-all', { method: 'POST' });
  await readJson(res);
}

// ==================== CUSTOMER–RESTAURANT MEMBERSHIP ====================

export interface CustomerRestaurantMembership {
  membershipId: string;
  restaurantId: string;
  displayName?: string;
  joinedAt: string;
  name: string;
  slug?: string;
  logoUrl?: string;
  cuisine?: string;
  currency?: string;
  status: string;
}

/** List all restaurants the current customer is a member of. */
export async function getMyRestaurants(): Promise<CustomerRestaurantMembership[]> {
  const res = await authenticatedFetch('/api/v1/customer-restaurants/my-restaurants');
  const data = await readJson<{ success: boolean; restaurants: any[] }>(res);
  return data.restaurants || [];
}

/** Join a restaurant (customer becomes a member). */
export async function joinRestaurant(restaurantId: string, displayName?: string): Promise<void> {
  const res = await authenticatedFetch('/api/v1/customer-restaurants/join', {
    method: 'POST',
    body: JSON.stringify({ restaurantId, displayName }),
  });
  await readJson(res);
}

/** Leave a restaurant. */
export async function leaveRestaurant(restaurantId: string): Promise<void> {
  const res = await authenticatedFetch(`/api/v1/customer-restaurants/leave/${restaurantId}`, {
    method: 'DELETE',
  });
  await readJson(res);
}

/** Select a restaurant after login — issues a restaurant-scoped JWT. */
export async function selectRestaurant(restaurantId: string): Promise<AuthResponse> {
  const res = await fetch(`${API_ENDPOINT}/select-restaurant`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('savory_token') || ''}`,
    },
    body: JSON.stringify({ restaurantId }),
  });
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.message || 'Failed to select restaurant');
  }
  return data;
}

// ==================== ADMIN: CUSTOMER MEMBERSHIP MANAGEMENT ====================

export interface CustomerMemberDetails {
  membershipId: string;
  customerId: string;
  displayName?: string;
  joinedAt: string;
  username?: string;
  email?: string;
  phone?: string;
  enabled?: boolean;
}

/** List all customer members of a restaurant (admin/manager view). */
export async function listCustomerMembers(restaurantId?: string): Promise<CustomerMemberDetails[]> {
  const params = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/customer-restaurants/members${params}`);
  const data = await readJson<{ success: boolean; members: any[] }>(res);
  return data.members || [];
}

/** Remove a customer from a restaurant (admin action). */
export async function removeCustomerMember(customerId: string, restaurantId?: string): Promise<void> {
  const params = restaurantId ? `?restaurantId=${encodeURIComponent(restaurantId)}` : '';
  const res = await authenticatedFetch(`/api/v1/customer-restaurants/members/${customerId}${params}`, {
    method: 'DELETE',
  });
  await readJson(res);
}

/** SSE stream URL — EventSource cannot send Authorization headers, token goes in query string. */
export function getRealtimeStreamUrl(): string {
  const token = localStorage.getItem('savory_token');
  const base = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
  return `${base}/api/v1/realtime/stream?token=${encodeURIComponent(token || '')}`;
}
