/**
 * Token Management & Authenticated API Calls
 * Handle JWT token storage, retrieval, and authenticated HTTP requests
 */

const TOKEN_KEY = 'savory_token';
const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

/**
 * Store JWT token in localStorage
 */
export function storeToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * Retrieve JWT token from localStorage
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * Remove JWT token from localStorage
 */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * Check if user is authenticated
 */
export function isAuthenticated(): boolean {
  return !!getToken();
}

/**
 * Make authenticated API request with Bearer token
 */
export async function authenticatedFetch(
  endpoint: string,
  options: RequestInit = {}
): Promise<Response> {
  const token = getToken();
  
  const headers = {
    ...options.headers,
    'Content-Type': 'application/json',
  } as Record<string, string>;

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const url = `${API_BASE_URL}${endpoint}`;
  
  return fetch(url, {
    ...options,
    headers,
  });
}

/**
 * Logout helper - clears token and redirects
 */
export function logout(redirectTo: string = '/'): void {
  removeToken();
  window.location.href = redirectTo;
}

/**
 * Get token expiry time
 */
export function getTokenExpiryTime(): Date | null {
  const token = getToken();
  if (!token) return null;

  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return new Date(payload.exp * 1000);
  } catch (error) {
    console.error('Error reading token expiry:', error);
    return null;
  }
}

/**
 * Decode token payload (for debugging)
 */
export function decodeToken(): any | null {
  const token = getToken();
  if (!token) return null;

  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    return JSON.parse(atob(parts[1]));
  } catch (error) {
    console.error('Error decoding token:', error);
    return null;
  }
}

/**
 * Decode the role claim from the stored JWT.
 */
export function getTokenRole(): string | null {
  const payload = decodeToken();
  return payload?.role || null;
}

/**
 * Decode the restaurantId claim from the stored JWT.
 */
export function getTokenRestaurantId(): string | null {
  const payload = decodeToken();
  return payload?.restaurantId || null;
}

/**
 * Decode the userId claim from the stored JWT.
 */
export function getTokenUserId(): string | null {
  const payload = decodeToken();
  return payload?.userId || null;
}

/**
 * True if the stored token belongs to a restaurant staff role.
 */
export function isStaffToken(): boolean {
  const role = getTokenRole();
  return role !== null && role !== 'ROLE_CUSTOMER';
}
