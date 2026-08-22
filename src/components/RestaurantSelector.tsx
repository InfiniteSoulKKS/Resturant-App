import React, { useState, useEffect } from 'react';
import { Store, MapPin, ChevronRight, LogOut, RefreshCw, X } from 'lucide-react';
import { getMyRestaurants, joinRestaurant, selectRestaurant, listRestaurants } from '../lib/apiClient';
import type { CustomerRestaurantMembership } from '../lib/apiClient';
import type { Restaurant } from '../types';

interface RestaurantSelectorProps {
  isOpen: boolean;
  onSelect: (restaurantId: string, token: string) => void;
  onSkip: () => void;
  onLogout: () => void;
  username: string;
}

/**
 * Shown after a customer logs in if they are a member of multiple restaurants.
 * Lets them pick which restaurant to operate in. If they have no memberships,
 * they can join a restaurant or skip (browse as guest).
 */
export const RestaurantSelector: React.FC<RestaurantSelectorProps> = ({
  isOpen,
  onSelect,
  onSkip,
  onLogout,
  username,
}) => {
  const [memberships, setMemberships] = useState<CustomerRestaurantMembership[]>([]);
  const [allRestaurants, setAllRestaurants] = useState<Restaurant[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSelecting, setIsSelecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showJoin, setShowJoin] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    loadData();
  }, [isOpen]);

  const loadData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [myRests, allRests] = await Promise.all([
        getMyRestaurants().catch(() => []),
        listRestaurants().catch(() => []),
      ]);
      setMemberships(myRests);
      setAllRestaurants(allRests);
    } catch {
      setError('Failed to load restaurants');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelect = async (restaurantId: string) => {
    setIsSelecting(true);
    setError(null);
    try {
      const response = await selectRestaurant(restaurantId);
      onSelect(restaurantId, response.token);
    } catch (err: any) {
      setError(err.message || 'Failed to select restaurant');
    } finally {
      setIsSelecting(false);
    }
  };

  const handleJoin = async (restaurantId: string) => {
    try {
      await joinRestaurant(restaurantId);
      // Reload memberships
      const updated = await getMyRestaurants().catch(() => []);
      setMemberships(updated);
      setShowJoin(false);
    } catch (err: any) {
      setError(err.message || 'Failed to join restaurant');
    }
  };

  if (!isOpen) return null;

  const availableToJoin = allRestaurants.filter(
    (r) => r.status === 'ACTIVE' && !memberships.some((m) => m.restaurantId === r.id)
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
      <div className="bg-stone-900/95 border border-stone-800 rounded-3xl max-w-lg w-full p-6 md:p-8 shadow-2xl relative my-8 text-stone-100">
        {/* Header */}
        <div className="text-center mb-6">
          <div className="w-12 h-12 bg-amber-500/10 border border-amber-500/20 rounded-2xl flex items-center justify-center text-amber-400 mx-auto mb-3">
            <Store className="w-6 h-6" />
          </div>
          <h2 className="text-lg font-bold font-serif text-stone-100">
            Welcome, {username}!
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            {memberships.length > 0
              ? 'Select a restaurant to continue'
              : 'Join a restaurant to get started'}
          </p>
        </div>

        {/* Error */}
        {error && (
          <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-xs text-rose-400 flex items-center gap-2">
            <span className="flex-1">{error}</span>
            <button
              type="button"
              onClick={() => setError(null)}
              className="text-rose-400 hover:text-rose-300 p-1 -m-1 rounded-lg hover:bg-rose-500/10 transition-colors cursor-pointer shrink-0"
              title="Dismiss"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <RefreshCw className="w-5 h-5 text-stone-400 animate-spin" />
          </div>
        ) : (
          <>
            {/* My Restaurants */}
            {memberships.length > 0 && (
              <div className="space-y-2 mb-4">
                <p className="text-[10px] font-mono uppercase tracking-widest text-stone-500 px-1">
                  Your Restaurants
                </p>
                {memberships.map((m) => (
                  <button
                    key={m.restaurantId}
                    onClick={() => handleSelect(m.restaurantId)}
                    disabled={isSelecting}
                    className="w-full flex items-center gap-3 p-3 bg-stone-800/50 hover:bg-stone-800 border border-stone-700 hover:border-amber-500/30 rounded-xl transition-all cursor-pointer group disabled:opacity-50"
                  >
                    <div className="w-10 h-10 rounded-xl overflow-hidden bg-stone-700 shrink-0 flex items-center justify-center">
                      {m.logoUrl ? (
                        <img src={m.logoUrl} alt={m.name} className="w-full h-full object-cover" />
                      ) : (
                        <Store className="w-5 h-5 text-amber-400" />
                      )}
                    </div>
                    <div className="min-w-0 text-left">
                      <p className="text-sm font-bold text-stone-100 truncate">{m.name}</p>
                      <p className="text-[10px] text-stone-500 flex items-center gap-1">
                        {m.cuisine && <span>{m.cuisine}</span>}
                        {m.currency && <span>· {m.currency}</span>}
                      </p>
                    </div>
                    <ChevronRight className="w-4 h-4 text-stone-600 group-hover:text-amber-400 transition-colors ml-auto shrink-0" />
                  </button>
                ))}
              </div>
            )}

            {/* Join a Restaurant */}
            {!showJoin && availableToJoin.length > 0 && (
              <button
                onClick={() => setShowJoin(true)}
                className="w-full py-2.5 bg-stone-800 hover:bg-stone-700 border border-stone-700 hover:border-amber-500/30 text-stone-300 text-xs font-bold rounded-xl transition-all cursor-pointer mb-4"
              >
                + Join Another Restaurant
              </button>
            )}

            {showJoin && availableToJoin.length > 0 && (
              <div className="space-y-2 mb-4">
                <div className="flex items-center justify-between px-1">
                  <p className="text-[10px] font-mono uppercase tracking-widest text-stone-500">
                    Available Restaurants
                  </p>
                  <button
                    onClick={() => setShowJoin(false)}
                    className="text-[10px] text-stone-500 hover:text-stone-300 cursor-pointer"
                  >
                    Cancel
                  </button>
                </div>
                {availableToJoin.map((r) => (
                  <button
                    key={r.id}
                    onClick={() => handleJoin(r.id)}
                    className="w-full flex items-center gap-3 p-3 bg-stone-800/30 hover:bg-stone-800/60 border border-stone-700/50 hover:border-emerald-500/30 rounded-xl transition-all cursor-pointer group"
                  >
                    <div className="w-9 h-9 rounded-xl overflow-hidden bg-stone-700 shrink-0 flex items-center justify-center">
                      {r.logoUrl ? (
                        <img src={r.logoUrl} alt={r.name} className="w-full h-full object-cover" />
                      ) : (
                        <Store className="w-4 h-4 text-stone-400" />
                      )}
                    </div>
                    <div className="min-w-0 text-left">
                      <p className="text-xs font-bold text-stone-200 truncate">{r.name}</p>
                      <p className="text-[10px] text-stone-500 flex items-center gap-1">
                        <MapPin className="w-3 h-3" />
                        {r.city || '—'} · {r.cuisine || 'Multi-cuisine'}
                      </p>
                    </div>
                    <span className="ml-auto text-[10px] font-bold text-emerald-400 group-hover:text-emerald-300 transition-colors shrink-0">
                      JOIN
                    </span>
                  </button>
                ))}
              </div>
            )}

            {/* Skip / Browse as Guest */}
            <div className="border-t border-stone-800 pt-4 mt-2 space-y-2">
              <button
                onClick={onSkip}
                className="w-full py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-400 hover:text-stone-200 text-xs font-bold rounded-xl border border-stone-700 transition-all cursor-pointer"
              >
                Browse as Guest
              </button>
              <button
                onClick={onLogout}
                className="w-full py-2 flex items-center justify-center gap-2 text-stone-500 hover:text-rose-400 text-[10px] font-bold transition-colors cursor-pointer"
              >
                <LogOut className="w-3 h-3" />
                Sign Out
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
