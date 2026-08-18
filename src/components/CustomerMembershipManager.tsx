import React, { useEffect, useState } from 'react';
import {
  UserCheck,
  Users,
  Trash2,
  X,
  CheckCircle2,
  AlertCircle,
  Mail,
  Phone,
  Calendar,
  Search,
} from 'lucide-react';
import { listCustomerMembers, removeCustomerMember } from '../lib/apiClient';
import type { CustomerMemberDetails } from '../lib/apiClient';

interface CustomerMembershipManagerProps {
  restaurantId: string;
  restaurantName?: string;
  /** Called after a member is removed so the parent can refresh derived state (e.g. badge count). */
  onMembersChanged?: () => void;
}

export const CustomerMembershipManager: React.FC<CustomerMembershipManagerProps> = ({
  restaurantId,
  restaurantName,
  onMembersChanged,
}) => {
  const [members, setMembers] = useState<CustomerMemberDetails[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [confirmRemove, setConfirmRemove] = useState<CustomerMemberDetails | null>(null);

  const load = async () => {
    setIsLoading(true);
    try {
      const data = await listCustomerMembers(restaurantId);
      setMembers(data);
    } catch {
      // silently fail
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, [restaurantId]);

  const handleRemove = async (member: CustomerMemberDetails) => {
    try {
      await removeCustomerMember(member.customerId, restaurantId);
      setMessage({
        type: 'ok',
        text: `✅ ${member.username || member.customerId} has been removed from this restaurant.`,
      });
      setConfirmRemove(null);
      await load();
      onMembersChanged?.();
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const filtered = members.filter((m) => {
    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    return (
      m.username?.toLowerCase().includes(q) ||
      m.email?.toLowerCase().includes(q) ||
      m.phone?.toLowerCase().includes(q) ||
      m.displayName?.toLowerCase().includes(q)
    );
  });

  const formatDate = (dateStr: string) => {
    try {
      return new Date(dateStr).toLocaleDateString('en-IN', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="pt-20 max-w-[1440px] mx-auto px-4 md:px-8 py-6 pb-28">
      {/* Header */}
      <div className="flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4 mb-8">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2.5">
            <Users className="w-8 h-8 text-sky-400" />
            <span>Customer Members</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            {restaurantName
              ? `Customers who have joined ${restaurantName}.`
              : 'Customers who have joined this restaurant.'}
            {' '}Total: <span className="text-stone-300 font-semibold">{members.length}</span> member{members.length !== 1 ? 's' : ''}.
          </p>
        </div>
      </div>

      {/* Message banner */}
      {message && (
        <div
          className={`mb-6 p-3 rounded-xl border text-xs flex items-center gap-2 ${
            message.type === 'ok'
              ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
              : 'bg-rose-500/10 border-rose-500/30 text-rose-400'
          }`}
        >
          {message.type === 'ok' ? (
            <CheckCircle2 className="w-4 h-4 shrink-0" />
          ) : (
            <AlertCircle className="w-4 h-4 shrink-0" />
          )}
          {message.text}
        </div>
      )}

      {/* Search bar */}
      <div className="relative mb-6">
        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-stone-400" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search by username, email, or phone..."
          className="w-full pl-9 pr-8 py-2.5 bg-stone-900/80 rounded-xl border border-stone-800 text-stone-200 placeholder-stone-500 text-xs focus:outline-none focus:border-sky-500/70 focus:ring-1 focus:ring-sky-500/70 transition-colors"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-stone-500 hover:text-stone-300"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        )}
      </div>

      {/* Members grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {filtered.map((m) => (
          <div
            key={m.membershipId}
            className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl flex flex-col gap-3"
          >
            {/* Avatar + Name */}
            <div className="flex items-center gap-3">
              <div className="w-11 h-11 bg-sky-500 text-stone-950 font-bold rounded-xl flex items-center justify-center uppercase shadow-md shadow-sky-500/20">
                {(m.username || m.customerId).charAt(0)}
              </div>
              <div className="min-w-0 flex-1">
                <h4 className="text-sm font-bold text-stone-100 truncate">
                  {m.username || 'Unknown'}
                </h4>
                {m.displayName && (
                  <p className="text-[11px] text-sky-400 truncate">
                    "{m.displayName}"
                  </p>
                )}
              </div>
              <span
                className={`w-2 h-2 rounded-full shrink-0 ${
                  m.enabled !== false ? 'bg-emerald-400' : 'bg-stone-500'
                }`}
              />
            </div>

            {/* Contact info */}
            <div className="space-y-1">
              {m.email && (
                <p className="text-[11px] text-stone-400 flex items-center gap-1.5 truncate">
                  <Mail className="w-3 h-3 shrink-0 text-stone-500" />
                  {m.email}
                </p>
              )}
              {m.phone && (
                <p className="text-[11px] text-stone-400 flex items-center gap-1.5">
                  <Phone className="w-3 h-3 shrink-0 text-stone-500" />
                  {m.phone}
                </p>
              )}
              <p className="text-[11px] text-stone-500 flex items-center gap-1.5">
                <Calendar className="w-3 h-3 shrink-0" />
                Joined {formatDate(m.joinedAt)}
              </p>
            </div>

            {/* Remove button */}
            <button
              onClick={() => setConfirmRemove(m)}
              className="mt-auto py-2 rounded-xl text-xs font-bold border transition-all cursor-pointer flex items-center justify-center gap-1.5 bg-stone-950 hover:bg-rose-950/40 text-stone-400 hover:text-rose-400 border-stone-800"
            >
              <Trash2 className="w-3.5 h-3.5" />
              Remove Member
            </button>
          </div>
        ))}
      </div>

      {/* Empty state */}
      {filtered.length === 0 && !isLoading && (
        <div className="text-center py-20 bg-stone-900/60 rounded-3xl border border-stone-800">
          <Users className="w-10 h-10 text-stone-600 mx-auto mb-2" />
          <p className="text-sm text-stone-300">
            {members.length === 0
              ? 'No customers have joined this restaurant yet.'
              : 'No members match your search.'}
          </p>
        </div>
      )}

      {/* Confirm remove modal */}
      {confirmRemove && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md">
          <div className="bg-stone-900 border border-stone-700 rounded-3xl max-w-sm w-full p-6 relative text-stone-100">
            <div className="flex justify-between items-center pb-4 border-b border-stone-800">
              <h3 className="text-base font-bold font-serif flex items-center gap-2">
                <Trash2 className="w-5 h-5 text-rose-400" />
                Remove Member
              </h3>
              <button
                onClick={() => setConfirmRemove(null)}
                className="text-stone-400 hover:text-stone-100 p-1"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="mt-4 space-y-3 text-xs">
              <p className="text-stone-300">
                Are you sure you want to remove{' '}
                <span className="font-bold text-stone-100">
                  {confirmRemove.username || confirmRemove.customerId}
                </span>{' '}
                from this restaurant?
              </p>
              <p className="text-stone-500">
                Their order history will be preserved, but they will no longer be
                able to select this restaurant after login.
              </p>

              <div className="flex gap-2 pt-2">
                <button
                  onClick={() => setConfirmRemove(null)}
                  className="flex-1 py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-300 font-bold rounded-xl cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  onClick={() => handleRemove(confirmRemove)}
                  className="flex-1 py-2.5 bg-rose-500 hover:bg-rose-400 text-white font-bold rounded-xl cursor-pointer"
                >
                  Remove
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
