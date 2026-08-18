import React, { useEffect, useState } from 'react';
import { Bell, X, CheckCheck, PackageCheck, ClipboardList, Users, AlertCircle } from 'lucide-react';
import { Notification } from '../types';
import { getMyNotifications, markNotificationsRead } from '../lib/apiClient';
import { getToken } from '../lib/tokenManager';

interface NotificationsBellProps {
  liveNotifications: Notification[];
}

const TYPE_ICONS: Record<string, React.ReactNode> = {
  ORDER_READY: <PackageCheck className="w-4 h-4 text-emerald-400" />,
  NEW_ORDER: <ClipboardList className="w-4 h-4 text-amber-400" />,
  ORDER_STATUS: <ClipboardList className="w-4 h-4 text-sky-400" />,
  STAFF: <Users className="w-4 h-4 text-violet-400" />,
};

export const NotificationsBell: React.FC<NotificationsBellProps> = ({ liveNotifications }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    if (!getToken()) return;
    getMyNotifications()
      .then((data) => {
        setNotifications(data.notifications);
        setUnread(data.unread);
      })
      .catch(() => {});
  }, [isOpen]);

  // Merge realtime notifications on top
  useEffect(() => {
    if (liveNotifications.length > 0) {
      setNotifications((prev) => {
        const ids = new Set(prev.map((n) => n.id));
        const fresh = liveNotifications.filter((n) => !ids.has(n.id));
        return fresh.length ? [...fresh, ...prev] : prev;
      });
      setUnread((u) => u + liveNotifications.length);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [liveNotifications]);

  const handleOpen = () => {
    setIsOpen((v) => !v);
    if (!isOpen && unread > 0) {
      markNotificationsRead().catch(() => {});
      setUnread(0);
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    }
  };

  if (!getToken()) return null;

  return (
    <div className="relative">
      <button
        onClick={handleOpen}
        className="relative p-2 rounded-xl text-stone-300 hover:text-stone-100 hover:bg-stone-800/80 transition-all cursor-pointer bg-stone-900 border border-stone-800"
        title="Notifications"
      >
        <Bell className="w-5 h-5 text-amber-400" />
        {unread > 0 && (
          <span className="absolute -top-1.5 -right-1.5 bg-rose-500 text-white font-bold text-[10px] min-w-5 h-5 px-1 rounded-full flex items-center justify-center shadow-md animate-pulse">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {isOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)} />
          <div className="absolute right-0 mt-2 w-80 sm:w-96 max-h-[420px] overflow-y-auto bg-stone-900 border border-stone-700 rounded-2xl shadow-2xl z-50 text-stone-100">
            <div className="sticky top-0 bg-stone-900/95 backdrop-blur p-3 border-b border-stone-800 flex justify-between items-center">
              <h4 className="text-xs font-bold font-mono uppercase tracking-widest text-stone-300 flex items-center gap-2">
                <Bell className="w-3.5 h-3.5 text-amber-400" />
                Notifications
              </h4>
              <button onClick={() => setIsOpen(false)} className="text-stone-400 hover:text-stone-100 p-1">
                <X className="w-4 h-4" />
              </button>
            </div>

            {notifications.length === 0 && (
              <div className="p-8 text-center text-xs text-stone-500 flex flex-col items-center gap-2">
                <CheckCheck className="w-8 h-8 text-stone-600" />
                You're all caught up!
              </div>
            )}

            {notifications.map((n) => (
              <div
                key={n.id}
                className={`p-3 border-b border-stone-800/70 flex gap-2.5 transition-colors ${
                  n.read ? 'opacity-60' : 'bg-amber-500/[0.04]'
                }`}
              >
                <div className="mt-0.5 shrink-0">
                  {TYPE_ICONS[n.type || 'SYSTEM'] || <AlertCircle className="w-4 h-4 text-stone-400" />}
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-semibold text-stone-100 leading-snug">{n.title}</p>
                  <p className="text-[11px] text-stone-400 mt-0.5 leading-snug">{n.message}</p>
                  <p className="text-[9px] font-mono text-stone-600 mt-1">
                    {n.createdAt ? new Date(n.createdAt).toLocaleString() : ''}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
};
