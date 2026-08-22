export type Category = 'All Items' | 'Appetizers' | 'Starters' | 'Mains' | 'Breads' | 'Desserts' | 'Beverages';

export interface MenuItem {
  id: string;
  restaurantId: string;
  title: string;
  description: string;
  price: number;
  category: 'Appetizers' | 'Starters' | 'Mains' | 'Breads' | 'Desserts' | 'Beverages';
  imageUrl: string;
  status: 'Available' | 'Sold Out';
  tag?: string;
  isVeg?: boolean; // True = Veg (Green), False = Non-Veg (Red)
  spiceLevel?: 'Mild' | 'Medium' | 'Spicy' | 'Fiery Hot';
  prepMinutes?: number;
  dailyPlateCount?: number | null; // null = unlimited plates
  createdAt?: string;
}

export interface CartItem {
  menuItem: MenuItem;
  quantity: number;
}

export type PaymentMethod = 'UPI' | 'RAZORPAY' | 'CARD' | 'CASH' | 'MOCK';
export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED';
export type OrderStatus = 'NEW' | 'PREPARING' | 'PACKED_READY' | 'COMPLETED' | 'DECLINED' | 'CANCELLED';
export type UserRole =
  | 'ROLE_CUSTOMER'
  | 'ROLE_CHEF'
  | 'ROLE_MANAGER'
  | 'ROLE_ADMIN'
  | 'ROLE_SUPER_ADMIN';

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  phone?: string;
  role: UserRole;
  restaurantId?: string;
  enabled?: boolean;
}

export interface Restaurant {
  id: string;
  name: string;
  slug?: string;
  description?: string;
  address?: string;
  city?: string;
  cuisine?: string;
  phone?: string;
  email?: string;
  logoUrl?: string;
  status: 'ACTIVE' | 'SUSPENDED';
  currency?: string;
  ownerId?: string;
  createdAt?: string;
}

export interface OrderItemSummary {
  id: string;
  title: string;
  price: number;
  quantity: number;
  notes?: string;
  menuItemId?: string;
}

export interface OrderNotification {
  id: string;
  orderId: string;
  orderNumber: string;
  title: string;
  message: string;
  channels: ('APP_PUSH' | 'SMS' | 'WHATSAPP' | 'EMAIL')[];
  timestamp: string;
  read?: boolean;
}

export interface Notification {
  id: string;
  userId: string;
  restaurantId?: string;
  orderId?: string;
  title: string;
  message: string;
  type?: 'ORDER_STATUS' | 'ORDER_READY' | 'NEW_ORDER' | 'STAFF' | 'SYSTEM';
  channel?: string;
  read?: boolean;
  createdAt?: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  restaurantId: string;
  restaurantName?: string;
  tableNumber?: number;
  guests?: number;
  orderType: 'PICKUP' | 'DINE_IN' | 'PRE_ORDER';
  pickupTime?: string;
  timeSlot: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  userId?: string;
  items: OrderItemSummary[];
  totalAmount: number;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
  paymentTransactionId?: string;
  orderStatus: OrderStatus;
  createdAt: string;
  timestamp: number;
  notificationsSent?: string[]; // Log of notifications sent e.g., ["SMS", "WhatsApp", "Email"]
  cancelReason?: string; // Reason for cancellation/decline
}

export interface Ingredient {
  id: string;
  restaurantId: string;
  name: string;
  displayName?: string;
  unit: string;
  category?: string;
  stockQuantity: number;
  reorderLevel: number;
  active?: boolean;
  updatedAt?: string;
}

export interface IngredientForecast {
  name: string;
  unit: string;
  requiredQuantity: number;
  currentStock: number;
  shortfall: number;
  needPurchase: boolean;
  reorderLevel: number;
}

/** Per-dish ingredient totals inside a forecast (from recipe × plates). */
export interface DishForecast {
  menuItemId: string;
  dish: string;
  plates: number;
  ingredients: { name: string; unit: string; requiredQuantity: number }[];
}

export interface IngredientForecastResponse {
  ingredients: IngredientForecast[];
  dishes: DishForecast[];
}

export interface OperatingHour {
  id?: number;
  restaurantId: string;
  dayOfWeek: number; // 1 = Monday .. 7 = Sunday
  openTime?: string; // "HH:MM"
  closeTime?: string; // "HH:MM"
  closed: boolean;
}

export interface PreOrderSettings {
  restaurantId: string;
  cutoffTime: string; // "HH:MM" — per-restaurant cutoff: orders for a date close at this time on the day before (D-1)
  advanceDays: number;
}

export interface DishAvailabilityView {
  menuItemId: string;
  days: number[]; // 1 = Monday .. 7 = Sunday
  overrides: { date: string; action: 'OPEN' | 'CLOSE' }[];
}

/** One row from POST /api/v1/pre-orders/dates — a selectable fulfillment day. */
export interface PreOrderDateOption {
  date: string; // yyyy-MM-dd
  weekday: string;
  openTime?: string | null;
  closeTime?: string | null;
  orderable: boolean;
  reasons: string[];
  dishes: { menuItemId: string; title: string; available: boolean; reason?: string }[];
}

export interface TableTypeConfig {
  type: string; // '2-Seater', '4-Seater', '6-Seater'
  count: number;
}

export interface TableAvailability {
  type: string;
  total: number;
  booked: number;
  remaining: number;
}

export interface PlateAvailability {
  menuItemId: string;
  title: string;
  dailyPlateCount: number | null;
  platesOrdered: number;
  remaining: number; // -1 = unlimited
  available: boolean;
}

/** Recipe row inside the menu create/edit form (name, qty per plate, unit). */
export interface RecipeIngredient {
  ingredientId?: string;
  name: string;
  quantityPerUnit: number;
  unit: string;
}

export interface PrepItem {
  id: string;
  itemName: string;
  category: 'Starters' | 'Mains' | 'Breads' | 'Desserts' | 'Beverages';
  requiredCount: number;
  preppedCount: number;
  tag: string;
  priority: 'Normal' | 'Priority';
  imageUrl: string;
}

export interface EstimatedRawMaterial {
  name: string;
  amount: string;
}

export type ViewTab =
  | 'customer_menu'
  | 'menu_management'
  | 'orders'
  | 'chef_prep'
  | 'spring_backend'
  | 'super_admin'
  | 'staff_management'
  | 'customer_memberships'
  | 'ingredients'
  | 'dashboard'
  | 'preorder_settings'
  | 'admin_dashboard';
