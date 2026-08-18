/**
 * Role helpers for the frontend.
 *
 * A user may hold more than one role — e.g. a kitchen lead who is both
 * MANAGER and CHEF ("ROLE_MANAGER,ROLE_CHEF"). Roles arrive as a
 * comma-separated string from the JWT claim / /auth/me profile, so every
 * role check must split on commas.
 */

export const ROLES = {
  SUPER_ADMIN: 'ROLE_SUPER_ADMIN',
  ADMIN: 'ROLE_ADMIN',
  MANAGER: 'ROLE_MANAGER',
  CHEF: 'ROLE_CHEF',
  CUSTOMER: 'ROLE_CUSTOMER',
} as const;

export const ROLE_LABELS: Record<string, string> = {
  [ROLES.SUPER_ADMIN]: 'Super Admin',
  [ROLES.ADMIN]: 'Admin',
  [ROLES.MANAGER]: 'Manager',
  [ROLES.CHEF]: 'Chef',
  [ROLES.CUSTOMER]: 'Customer',
};

/** Split a possibly comma-separated role string into individual roles. */
export function parseRoles(role?: string | null): string[] {
  if (!role) return [];
  return role
    .split(',')
    .map((r) => r.trim())
    .filter(Boolean);
}

/** True if the user holds the given role (e.g. 'ROLE_CHEF'). */
export function hasRole(role: string | null | undefined, target: string): boolean {
  return parseRoles(role).includes(target);
}

/** True if the user holds ANY of the given roles. */
export function hasAnyRole(role: string | null | undefined, ...targets: string[]): boolean {
  return targets.some((t) => hasRole(role, t));
}

/** True for manager, admin, or super admin (menu/pricing/order management authority). */
export function canManage(role: string | null | undefined): boolean {
  return hasAnyRole(role, ROLES.MANAGER, ROLES.ADMIN, ROLES.SUPER_ADMIN);
}

/** True if the user is any restaurant staff role (not customer / super admin). */
export function isStaffRole(role: string | null | undefined): boolean {
  return !!role && role !== ROLES.CUSTOMER && role !== ROLES.SUPER_ADMIN;
}

/** Human-readable label for one or more roles, e.g. "Manager + Chef". */
export function formatRoles(role?: string | null): string {
  const labels = parseRoles(role).map((r) => ROLE_LABELS[r] || r.replace('ROLE_', ''));
  return labels.length > 0 ? labels.join(' + ') : '—';
}
