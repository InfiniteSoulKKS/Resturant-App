import { initializeApp } from 'firebase/app';
import {
  getFirestore,
  collection,
  doc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  onSnapshot,
  query,
  orderBy,
  getDocFromServer
} from 'firebase/firestore';
import firebaseConfig from '../../../firebase-applet-config.json';
import { MenuItem, Order, PrepItem } from '../types';
import { INITIAL_MENU_ITEMS, INITIAL_ORDERS, INITIAL_PREP_ITEMS } from '../data/initialData';

const app = initializeApp(firebaseConfig);
export const db = getFirestore(app, firebaseConfig.firestoreDatabaseId);

export enum OperationType {
  CREATE = 'create',
  UPDATE = 'update',
  DELETE = 'delete',
  LIST = 'list',
  GET = 'get',
  WRITE = 'write',
}

export function handleFirestoreError(error: unknown, operationType: OperationType, path: string | null) {
  const errInfo = {
    error: error instanceof Error ? error.message : String(error),
    operationType,
    path
  };
  console.error('Firestore Error:', JSON.stringify(errInfo));
  return errInfo;
}

export async function testConnection() {
  try {
    await getDocFromServer(doc(db, 'test', 'connection'));
  } catch (error) {
    if (error instanceof Error && error.message.includes('offline')) {
      console.warn('Firebase client is offline or initializing.');
    }
  }
}

// Helper to remove undefined properties before sending to Firestore
function cleanData<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj));
}

// Seed initial data if Firestore collections are empty
export async function seedInitialDataIfEmpty() {
  try {
    const menuSnap = await getDocs(collection(db, 'menuItems'));
    if (menuSnap.empty) {
      for (const item of INITIAL_MENU_ITEMS) {
        await setDoc(doc(db, 'menuItems', item.id), cleanData(item));
      }
    }

    const ordersSnap = await getDocs(collection(db, 'orders'));
    if (ordersSnap.empty) {
      for (const ord of INITIAL_ORDERS) {
        await setDoc(doc(db, 'orders', ord.id), cleanData(ord));
      }
    }

    const prepSnap = await getDocs(collection(db, 'prepItems'));
    if (prepSnap.empty) {
      for (const prep of INITIAL_PREP_ITEMS) {
        await setDoc(doc(db, 'prepItems', prep.id), cleanData(prep));
      }
    }
  } catch (err) {
    console.warn('Seeding notice:', err);
  }
}

// Realtime listeners with REST fallback
export function subscribeMenuItems(callback: (items: MenuItem[]) => void) {
  const path = 'menuItems';
  const fetchRestMenu = async () => {
    try {
      const res = await fetch('/api/v1/menu');
      if (res.ok) {
        const data = await res.json();
        if (data && data.length > 0) {
          callback(data);
          return;
        }
      }
    } catch (e) {
      console.warn('REST menu fetch fallback error:', e);
    }
    callback(INITIAL_MENU_ITEMS);
  };

  return onSnapshot(
    collection(db, path),
    (snapshot) => {
      const items: MenuItem[] = [];
      snapshot.forEach((docSnap) => {
        items.push({ id: docSnap.id, ...docSnap.data() } as MenuItem);
      });
      if (items.length > 0) {
        callback(items);
      } else {
        fetchRestMenu();
      }
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path);
      fetchRestMenu();
    }
  );
}

export function subscribeOrders(callback: (orders: Order[]) => void) {
  const path = 'orders';
  const fetchRestOrders = async () => {
    try {
      const res = await fetch('/api/v1/orders');
      if (res.ok) {
        const data = await res.json();
        if (data && data.length > 0) {
          callback(data);
          return;
        }
      }
    } catch (e) {
      console.warn('REST orders fetch fallback error:', e);
    }
    callback(INITIAL_ORDERS);
  };

  return onSnapshot(
    collection(db, path),
    (snapshot) => {
      const orders: Order[] = [];
      snapshot.forEach((docSnap) => {
        orders.push({ id: docSnap.id, ...docSnap.data() } as Order);
      });
      if (orders.length > 0) {
        orders.sort((a, b) => new Date(b.timestamp || 0).getTime() - new Date(a.timestamp || 0).getTime());
        callback(orders);
      } else {
        fetchRestOrders();
      }
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path);
      fetchRestOrders();
    }
  );
}

export function subscribePrepItems(callback: (prepItems: PrepItem[]) => void) {
  const path = 'prepItems';
  const fetchRestPrep = async () => {
    try {
      const res = await fetch('/api/v1/prep-summary');
      if (res.ok) {
        const data = await res.json();
        if (data && data.length > 0) {
          callback(data);
          return;
        }
      }
    } catch (e) {
      console.warn('REST prep summary fetch fallback error:', e);
    }
    callback(INITIAL_PREP_ITEMS);
  };

  return onSnapshot(
    collection(db, path),
    (snapshot) => {
      const items: PrepItem[] = [];
      snapshot.forEach((docSnap) => {
        items.push({ id: docSnap.id, ...docSnap.data() } as PrepItem);
      });
      if (items.length > 0) {
        callback(items);
      } else {
        fetchRestPrep();
      }
    },
    (error) => {
      handleFirestoreError(error, OperationType.LIST, path);
      fetchRestPrep();
    }
  );
}

// Mutations with real-time REST API sync
export async function addMenuItemDB(item: Omit<MenuItem, 'id'>) {
  const path = 'menuItems';
  const id = 'm_' + Date.now();
  const newItem: MenuItem = { ...item, id, createdAt: new Date().toISOString() };

  // Trigger REST POST call to backend
  fetch('/api/v1/menu', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(newItem)
  }).catch((e) => console.warn('REST POST menu error:', e));

  try {
    await setDoc(doc(db, path, id), cleanData(newItem));
    return newItem;
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, path);
    return newItem;
  }
}

export async function updateMenuItemDB(id: string, updates: Partial<MenuItem>) {
  const path = `menuItems/${id}`;

  // Trigger REST PUT call to backend
  fetch(`/api/v1/menu/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(updates)
  }).catch((e) => console.warn('REST PUT menu error:', e));

  try {
    await updateDoc(doc(db, 'menuItems', id), cleanData(updates));
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function deleteMenuItemDB(id: string) {
  const path = `menuItems/${id}`;

  // Trigger REST DELETE call to backend
  fetch(`/api/v1/menu/${id}`, {
    method: 'DELETE'
  }).catch((e) => console.warn('REST DELETE menu error:', e));

  try {
    await deleteDoc(doc(db, 'menuItems', id));
  } catch (error) {
    handleFirestoreError(error, OperationType.DELETE, path);
  }
}

export async function addOrderDB(order: Order) {
  const path = 'orders';

  // Trigger REST POST call to backend
  fetch('/api/v1/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(order)
  }).catch((e) => console.warn('REST POST order error:', e));

  try {
    await setDoc(doc(db, path, order.id), cleanData(order));
  } catch (error) {
    handleFirestoreError(error, OperationType.CREATE, path);
  }
}

export async function updateOrderStatusDB(id: string, orderStatus: Order['orderStatus']) {
  const path = `orders/${id}`;

  // Trigger REST PUT call to backend
  fetch(`/api/v1/orders/${id}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ orderStatus })
  }).catch((e) => console.warn('REST PUT order status error:', e));

  try {
    await updateDoc(doc(db, 'orders', id), { orderStatus });
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}

export async function updatePrepCountDB(id: string, delta: number) {
  const path = `prepItems/${id}`;
  try {
    const docRef = doc(db, 'prepItems', id);
    const snap = await getDocFromServer(docRef);
    if (snap.exists()) {
      const current = snap.data().preppedCount || 0;
      const required = snap.data().requiredCount || 0;
      const newCount = Math.max(0, Math.min(required, current + delta));

      // Trigger REST PUT call to backend
      fetch(`/api/v1/prep-summary/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ preppedCount: newCount })
      }).catch((e) => console.warn('REST PUT prep error:', e));

      await updateDoc(docRef, { preppedCount: newCount });
    }
  } catch (error) {
    handleFirestoreError(error, OperationType.UPDATE, path);
  }
}
