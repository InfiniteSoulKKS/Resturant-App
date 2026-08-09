export type Category = 'All Items' | 'Appetizers' | 'Starters' | 'Mains' | 'Breads' | 'Desserts' | 'Beverages';

export interface MenuItem {
  id: string;
  title: string;
  description: string;
  price: number;
  category: 'Appetizers' | 'Starters' | 'Mains' | 'Breads' | 'Desserts' | 'Beverages';
  imageUrl: string;
  status: 'Available' | 'Sold Out';
  tag?: string;
  isVeg?: boolean; // True = Veg (Green), False = Non-Veg (Red)
  spiceLevel?: 'Mild' | 'Medium' | 'Spicy';
  createdAt?: string;
}

export interface CartItem {
  menuItem: MenuItem;
  quantity: number;
}

export type PaymentMethod = 'UPI' | 'RAZORPAY' | 'CARD' | 'CASH';
export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED';
export type OrderStatus = 'NEW' | 'ACCEPTED' | 'PREPARING' | 'PACKED_READY' | 'COMPLETED' | 'DECLINED';
export type UserRole = 'ROLE_CUSTOMER' | 'ROLE_CHEF' | 'ROLE_ADMIN';

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  phone?: string;
  role: UserRole;
}

export interface OrderItemSummary {
  id: string;
  title: string;
  price: number;
  quantity: number;
  notes?: string;
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

export interface Order {
  id: string;
  orderNumber: string;
  tableNumber?: number;
  guests?: number;
  orderType: 'PICKUP' | 'DINE_IN';
  pickupTime?: string;
  timeSlot: string;
  customerName: string;
  customerPhone?: string;
  customerEmail?: string;
  items: OrderItemSummary[];
  totalAmount: number;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
  paymentTransactionId?: string;
  orderStatus: OrderStatus;
  createdAt: string;
  timestamp: number;
  notificationsSent?: string[]; // Log of notifications sent e.g., ["SMS", "WhatsApp", "Email"]
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

export type ViewTab = 'customer_menu' | 'menu_management' | 'orders' | 'chef_prep' | 'spring_backend';

