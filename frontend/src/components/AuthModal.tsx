import React, { useState } from 'react';
import {
  ShieldCheck,
  Lock,
  User,
  Mail,
  Phone,
  KeyRound,
  CheckCircle2,
  AlertCircle,
  X,
  LogOut,
  Smartphone,
  Sparkles,
  RefreshCw,
  LogIn,
  UserPlus,
} from 'lucide-react';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentUser: any;
  onLoginSuccess: (user: any, token: string, refreshToken?: string) => void;
  onLogout: () => void;
}

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  onClose,
  currentUser,
  onLoginSuccess,
  onLogout,
}) => {
  const [authMode, setAuthMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');

  // Login Method State ('OTP' or 'PASSWORD')
  const [loginMethod, setLoginMethod] = useState<'OTP' | 'PASSWORD'>('OTP');
  const [loginPhoneOrEmail, setLoginPhoneOrEmail] = useState('+91 98765 43210');
  const [loginOtpSent, setLoginOtpSent] = useState(false);
  const [loginOtpCode, setLoginOtpCode] = useState('');
  const [loginDemoOtpCode, setLoginDemoOtpCode] = useState<string | null>(null);

  // Login Form State
  const [loginIdentifier, setLoginIdentifier] = useState('guest@example.com');
  const [loginPassword, setLoginPassword] = useState('Savory123!');

  // Register Form State
  const [regUsername, setRegUsername] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPhone, setRegPhone] = useState('+91 98765 43210');
  const [regPassword, setRegPassword] = useState('');
  const [regRole, setRegRole] = useState<'ROLE_CUSTOMER' | 'ROLE_CHEF' | 'ROLE_ADMIN'>('ROLE_CUSTOMER');

  // OTP State
  const [otpSent, setOtpSent] = useState(false);
  const [otpCode, setOtpCode] = useState('');
  const [demoOtpCode, setDemoOtpCode] = useState<string | null>(null);
  const [isOtpVerified, setIsOtpVerified] = useState(false);

  // UI state
  const [isLoading, setIsLoading] = useState(false);
  const [isOtpLoading, setIsOtpLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSendLoginOtp = async () => {
    if (!loginPhoneOrEmail) {
      setErrorMessage("Please enter a mobile number or email address.");
      return;
    }

    setIsOtpLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const res = await fetch('/api/v1/auth/send-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phoneOrEmail: loginPhoneOrEmail }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'Failed to send OTP.');
      }

      setLoginOtpSent(true);
      setLoginOtpCode(''); // Keep input blank so user types actual OTP
      if (data.demoOtp) {
        setLoginDemoOtpCode(data.demoOtp);
      }
      setSuccessMessage(`📱 6-Digit OTP code dispatched to ${loginPhoneOrEmail}. Please enter the code below to verify.`);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to send OTP.');
    } finally {
      setIsOtpLoading(false);
    }
  };

  const handleLoginWithOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginOtpCode) {
      setErrorMessage("Please enter the 6-digit OTP code.");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const res = await fetch('/api/v1/auth/login-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          phoneOrEmail: loginPhoneOrEmail,
          otp: loginOtpCode,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'OTP authentication failed.');
      }

      if (data.refreshToken) {
        localStorage.setItem('savory_refresh_token', data.refreshToken);
      }
      onLoginSuccess(data.user, data.token, data.refreshToken);

      setSuccessMessage(`Welcome back, ${data.user.username}! Authenticated via OTP with 30-day session.`);
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      setErrorMessage(err.message || 'OTP verification error.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          emailOrUsername: loginIdentifier,
          password: loginPassword,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'Login failed. Please check credentials.');
      }

      // Successful login - persist 30 days
      if (data.refreshToken) {
        localStorage.setItem('savory_refresh_token', data.refreshToken);
      }
      onLoginSuccess(data.user, data.token, data.refreshToken);

      setSuccessMessage(`Welcome back, ${data.user.username}! Authenticated with 30-day persistent session.`);
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      setErrorMessage(err.message || 'Authentication error.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendOtp = async () => {
    const target = regPhone || regEmail;
    if (!target) {
      setErrorMessage("Please enter a valid mobile number or email address.");
      return;
    }

    setIsOtpLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const res = await fetch('/api/v1/auth/send-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phoneOrEmail: target }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'Failed to dispatch OTP.');
      }

      setOtpSent(true);
      setOtpCode(''); // Keep input blank so user types actual OTP
      if (data.demoOtp) {
        setDemoOtpCode(data.demoOtp);
      }
      setSuccessMessage(`📱 6-Digit OTP code dispatched to ${target}. Please enter the code below to verify.`);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to send OTP.');
    } finally {
      setIsOtpLoading(false);
    }
  };

  const handleVerifyOtp = async () => {
    const target = regPhone || regEmail;
    if (!target || !otpCode) {
      setErrorMessage("Please enter the 6-digit OTP code.");
      return;
    }

    setIsOtpLoading(true);
    setErrorMessage(null);

    try {
      const res = await fetch('/api/v1/auth/verify-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phoneOrEmail: target, otp: otpCode }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'OTP verification failed.');
      }

      setIsOtpVerified(true);
      setSuccessMessage("✅ Mobile/Email verified via OTP! Click 'Complete Registration' below.");
    } catch (err: any) {
      setErrorMessage(err.message || 'Invalid OTP entered.');
    } finally {
      setIsOtpLoading(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!otpSent) {
      setErrorMessage("Please send and verify OTP before completing registration.");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);

    try {
      const res = await fetch('/api/v1/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: regUsername,
          email: regEmail,
          phone: regPhone,
          password: regPassword,
          role: regRole,
          otp: otpCode,
        }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.message || 'Registration failed.');
      }

      if (data.refreshToken) {
        localStorage.setItem('savory_refresh_token', data.refreshToken);
      }
      onLoginSuccess(data.user, data.token, data.refreshToken);

      setSuccessMessage(`Verified registration complete! Logged in as ${data.user.username} (${data.user.role}) for 30 days.`);
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      setErrorMessage(err.message || 'Registration error.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
      <div className="bg-stone-900/95 border border-stone-800 rounded-3xl max-w-md w-full p-6 md:p-8 shadow-2xl relative my-8 text-stone-100">
        {/* Header */}
        <div className="flex justify-between items-center pb-4 border-b border-stone-800">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 bg-amber-500/10 border border-amber-500/20 rounded-2xl flex items-center justify-center text-amber-400">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold font-serif text-stone-100 tracking-tight">Authentication & Security</h3>
              <p className="text-[10px] font-mono text-emerald-400">SMS / WhatsApp OTP • 30-Day Session</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-stone-400 hover:text-stone-100 p-1.5 rounded-xl hover:bg-stone-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Feedback Messages */}
        {errorMessage && (
          <div className="mt-4 p-3 bg-rose-500/10 border border-rose-500/30 rounded-xl text-xs text-rose-400 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{errorMessage}</span>
          </div>
        )}

        {successMessage && (
          <div className="mt-4 p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-xs text-emerald-400 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 shrink-0" />
            <span>{successMessage}</span>
          </div>
        )}

        {/* LOGGED IN USER STATE */}
        {currentUser ? (
          <div className="py-6 space-y-5">
            <div className="bg-stone-950 p-4 rounded-2xl border border-stone-800 space-y-3">
              <div className="flex justify-between items-center pb-3 border-b border-stone-800">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-amber-500 text-stone-950 font-bold rounded-xl flex items-center justify-center text-lg uppercase shadow-md shadow-amber-500/20">
                    {currentUser.username ? currentUser.username.charAt(0) : 'U'}
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-stone-100">{currentUser.username}</h4>
                    <p className="text-xs text-stone-400 font-mono">{currentUser.email}</p>
                  </div>
                </div>
                <span className="text-[10px] font-mono font-bold px-2.5 py-1 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/30">
                  {currentUser.role || 'ROLE_CUSTOMER'}
                </span>
              </div>

              <div className="text-[11px] font-mono text-stone-400 space-y-1.5 pt-1">
                <div className="flex justify-between">
                  <span>Session Type:</span>
                  <span className="text-emerald-400 font-bold">30-Day Persistent Token</span>
                </div>
                <div className="flex justify-between">
                  <span>Background Refresh:</span>
                  <span className="text-amber-400 font-bold">Auto-Renew Enabled</span>
                </div>
                <div className="flex justify-between">
                  <span>OTP Verification:</span>
                  <span className="text-emerald-400">SMS / WhatsApp Verified</span>
                </div>
              </div>
            </div>

            <button
              type="button"
              onClick={onLogout}
              className="w-full py-2.5 bg-stone-800 hover:bg-stone-700 text-stone-200 text-xs font-bold rounded-xl border border-stone-700 transition-colors cursor-pointer flex items-center justify-center gap-2"
            >
              <LogOut className="w-4 h-4 text-stone-400" />
              <span>Sign Out Session</span>
            </button>
          </div>
        ) : (
          /* LOG IN / REGISTER TABS AND FORM */
          <div className="pt-4 space-y-4">
            {/* Mode Switcher */}
            <div className="grid grid-cols-2 p-1 bg-stone-950 rounded-2xl border border-stone-800 text-xs font-medium">
              <button
                type="button"
                onClick={() => { setAuthMode('LOGIN'); setErrorMessage(null); }}
                className={`py-2 rounded-xl transition-all cursor-pointer ${
                  authMode === 'LOGIN'
                    ? 'bg-amber-500 text-stone-950 font-bold shadow'
                    : 'text-stone-400 hover:text-stone-100'
                }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => { setAuthMode('REGISTER'); setErrorMessage(null); }}
                className={`py-2 rounded-xl transition-all cursor-pointer ${
                  authMode === 'REGISTER'
                    ? 'bg-amber-500 text-stone-950 font-bold shadow'
                    : 'text-stone-400 hover:text-stone-100'
                }`}
              >
                Register with OTP
              </button>
            </div>

            {authMode === 'LOGIN' ? (
              <div className="space-y-4 text-xs">
                {/* Login Method Sub-toggle */}
                <div className="flex gap-2 p-1 bg-stone-950/80 rounded-xl border border-stone-800 text-[11px]">
                  <button
                    type="button"
                    onClick={() => { setLoginMethod('OTP'); setErrorMessage(null); }}
                    className={`flex-1 py-1.5 rounded-lg font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                      loginMethod === 'OTP'
                        ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                        : 'text-stone-400 hover:text-stone-200'
                    }`}
                  >
                    <Smartphone className="w-3.5 h-3.5" />
                    <span>OTP Login (SMS / Phone)</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => { setLoginMethod('PASSWORD'); setErrorMessage(null); }}
                    className={`flex-1 py-1.5 rounded-lg font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5 ${
                      loginMethod === 'PASSWORD'
                        ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                        : 'text-stone-400 hover:text-stone-200'
                    }`}
                  >
                    <KeyRound className="w-3.5 h-3.5" />
                    <span>Password Login</span>
                  </button>
                </div>

                {loginMethod === 'OTP' ? (
                  <form onSubmit={handleLoginWithOtpSubmit} className="space-y-3">
                    <div>
                      <label className="block text-stone-400 font-medium mb-1">
                        Mobile Phone (+91) or Email Address
                      </label>
                      <input
                        type="text"
                        required
                        value={loginPhoneOrEmail}
                        onChange={(e) => setLoginPhoneOrEmail(e.target.value)}
                        placeholder="+91 98765 43210 or guest@example.com"
                        className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2 text-stone-100 font-mono"
                      />
                    </div>

                    {/* OTP Box */}
                    <div className="bg-stone-950 p-3 rounded-2xl border border-stone-800 space-y-2">
                      <div className="flex justify-between items-center text-[11px]">
                        <span className="font-bold text-emerald-400 font-mono flex items-center gap-1">
                          <Smartphone className="w-3.5 h-3.5" />
                          6-Digit OTP Verification
                        </span>
                        {!loginOtpSent ? (
                          <button
                            type="button"
                            onClick={handleSendLoginOtp}
                            disabled={isOtpLoading}
                            className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-lg transition-all text-[10px] cursor-pointer"
                          >
                            {isOtpLoading ? "Sending..." : "Send OTP"}
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={handleSendLoginOtp}
                            className="text-[10px] text-stone-400 hover:text-stone-100 underline cursor-pointer"
                          >
                            Resend OTP
                          </button>
                        )}
                      </div>

                      {loginOtpSent && (
                        <div className="space-y-1.5">
                          <input
                            type="text"
                            maxLength={6}
                            required
                            value={loginOtpCode}
                            onChange={(e) => setLoginOtpCode(e.target.value)}
                            placeholder="Enter 6-Digit OTP"
                            className="w-full bg-stone-900 border border-stone-800 rounded-xl px-3 py-2 text-stone-100 font-mono text-center tracking-widest text-sm font-bold focus:border-emerald-500 focus:outline-none"
                          />
                          {loginDemoOtpCode && (
                            <p className="text-[10px] text-emerald-400 font-mono">
                              Simulated SMS OTP Code: <span className="font-bold underline">{loginDemoOtpCode}</span>
                            </p>
                          )}
                        </div>
                      )}
                    </div>

                    <button
                      type="submit"
                      disabled={isLoading || !loginOtpSent}
                      className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold rounded-xl shadow-lg shadow-amber-500/20 transition-all cursor-pointer flex items-center justify-center gap-2 active:scale-95 disabled:opacity-50"
                    >
                      {isLoading ? (
                        <span className="w-4 h-4 border-2 border-stone-950/20 border-t-stone-950 rounded-full animate-spin"></span>
                      ) : (
                        <LogIn className="w-4 h-4" />
                      )}
                      <span>Verify & Sign In via OTP</span>
                    </button>
                  </form>
                ) : (
                  <form onSubmit={handleLoginSubmit} className="space-y-4">
                    <div>
                      <label className="block text-stone-400 font-medium mb-1">
                        Email or Username
                      </label>
                      <input
                        type="text"
                        required
                        value={loginIdentifier}
                        onChange={(e) => setLoginIdentifier(e.target.value)}
                        placeholder="guest@example.com or chef_executive"
                        className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2.5 text-stone-100"
                      />
                    </div>

                    <div>
                      <label className="block text-stone-400 font-medium mb-1">
                        Password
                      </label>
                      <input
                        type="password"
                        required
                        value={loginPassword}
                        onChange={(e) => setLoginPassword(e.target.value)}
                        placeholder="••••••••••••"
                        className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2.5 text-stone-100"
                      />
                    </div>

                    <div className="bg-stone-950 p-3 rounded-2xl border border-stone-800 space-y-1.5">
                      <span className="text-[10px] text-stone-500 font-bold uppercase tracking-wider block">
                        Quick Demo Role Logins
                      </span>
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          onClick={() => {
                            setLoginIdentifier('manager_admin');
                            setLoginPassword('Savory123!');
                          }}
                          className="text-[10px] px-2.5 py-1 bg-purple-950/80 hover:bg-purple-900 text-purple-300 rounded-lg border border-purple-800/80 font-mono cursor-pointer font-bold"
                        >
                          👔 Manager Admin
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setLoginIdentifier('chef_executive');
                            setLoginPassword('Savory123!');
                          }}
                          className="text-[10px] px-2.5 py-1 bg-amber-950/80 hover:bg-amber-900 text-amber-300 rounded-lg border border-amber-800/80 font-mono cursor-pointer font-bold"
                        >
                          👨‍🍳 Chef
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setLoginIdentifier('guest@example.com');
                            setLoginPassword('Savory123!');
                          }}
                          className="text-[10px] px-2.5 py-1 bg-stone-800 hover:bg-stone-700 text-stone-300 rounded-lg border border-stone-700 font-mono cursor-pointer font-bold"
                        >
                          🍽️ Customer Guest
                        </button>
                      </div>
                    </div>

                    <button
                      type="submit"
                      disabled={isLoading}
                      className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold rounded-xl shadow-lg shadow-amber-500/20 transition-all cursor-pointer flex items-center justify-center gap-2 active:scale-95 disabled:opacity-50"
                    >
                      {isLoading ? (
                        <span className="w-4 h-4 border-2 border-stone-950/20 border-t-stone-950 rounded-full animate-spin"></span>
                      ) : (
                        <LogIn className="w-4 h-4" />
                      )}
                      <span>Sign In with Password</span>
                    </button>
                  </form>
                )}
              </div>
            ) : (
              <form onSubmit={handleRegisterSubmit} className="space-y-3 text-xs">
                <div>
                  <label className="block text-stone-400 font-medium mb-1">
                    Username
                  </label>
                  <input
                    type="text"
                    required
                    value={regUsername}
                    onChange={(e) => setRegUsername(e.target.value)}
                    placeholder="e.g. rahul_sharma"
                    className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2 text-stone-100"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-stone-400 font-medium mb-1">
                      Email Address
                    </label>
                    <input
                      type="email"
                      required
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      placeholder="rahul@example.com"
                      className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2 text-stone-100"
                    />
                  </div>
                  <div>
                    <label className="block text-stone-400 font-medium mb-1">
                      Mobile (+91)
                    </label>
                    <input
                      type="tel"
                      required
                      value={regPhone}
                      onChange={(e) => setRegPhone(e.target.value)}
                      placeholder="+91 98765 43210"
                      className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2 text-stone-100 font-mono"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-stone-400 font-medium mb-1">
                    Password (BCrypt Hashed)
                  </label>
                  <input
                    type="password"
                    required
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    placeholder="Minimum 8 characters"
                    className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2 text-stone-100"
                  />
                </div>

                <div>
                  <label className="block text-stone-400 font-medium mb-1">
                    User Role
                  </label>
                  <select
                    value={regRole}
                    onChange={(e: any) => setRegRole(e.target.value)}
                    className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none rounded-xl px-3 py-2 text-stone-100 font-mono"
                  >
                    <option value="ROLE_CUSTOMER">ROLE_CUSTOMER (Dining Guest)</option>
                    <option value="ROLE_CHEF">ROLE_CHEF (Kitchen Operations)</option>
                    <option value="ROLE_ADMIN">ROLE_ADMIN (Restaurant Manager)</option>
                  </select>
                </div>

                {/* OTP Verification Box */}
                <div className="bg-stone-950 p-3 rounded-2xl border border-stone-800 space-y-2">
                  <div className="flex justify-between items-center text-[11px]">
                    <span className="font-bold text-emerald-400 font-mono flex items-center gap-1">
                      <Smartphone className="w-3.5 h-3.5" />
                      SMS / WhatsApp OTP
                    </span>
                    {!otpSent ? (
                      <button
                        type="button"
                        onClick={handleSendOtp}
                        disabled={isOtpLoading}
                        className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-lg transition-all text-[10px] cursor-pointer"
                      >
                        {isOtpLoading ? "Sending..." : "Send OTP"}
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={handleSendOtp}
                        className="text-[10px] text-stone-400 hover:text-stone-100 underline cursor-pointer"
                      >
                        Resend OTP
                      </button>
                    )}
                  </div>

                  {otpSent && (
                    <div className="space-y-2">
                      <div className="flex gap-2">
                        <input
                          type="text"
                          maxLength={6}
                          value={otpCode}
                          onChange={(e) => setOtpCode(e.target.value)}
                          placeholder="6-Digit OTP"
                          className="flex-1 bg-stone-900 border border-stone-800 rounded-xl px-3 py-1.5 text-stone-100 font-mono text-center tracking-widest text-sm font-bold"
                        />
                        <button
                          type="button"
                          onClick={handleVerifyOtp}
                          disabled={isOtpLoading || isOtpVerified}
                          className={`px-3 py-1.5 rounded-xl font-bold text-xs transition-all cursor-pointer ${
                            isOtpVerified
                              ? 'bg-emerald-600 text-white'
                              : 'bg-amber-500 hover:bg-amber-400 text-stone-950'
                          }`}
                        >
                          {isOtpVerified ? "Verified ✓" : "Verify"}
                        </button>
                      </div>
                      {demoOtpCode && (
                        <p className="text-[10px] text-emerald-400 font-mono">
                          Simulated SMS OTP Code: <span className="font-bold underline">{demoOtpCode}</span>
                        </p>
                      )}
                    </div>
                  )}
                </div>

                <div className="bg-stone-950 p-2 rounded-xl border border-stone-800 text-[10px] font-mono text-stone-400 flex items-center justify-between">
                  <span>🔒 30-Day Session Lifetime</span>
                  <span className="text-emerald-400">Background JWT Auto-Refresh</span>
                </div>

                <button
                  type="submit"
                  disabled={isLoading || !otpSent}
                  className="w-full py-3 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold rounded-xl shadow-lg shadow-amber-500/20 transition-all cursor-pointer flex items-center justify-center gap-2 active:scale-95 disabled:opacity-50"
                >
                  {isLoading ? (
                    <span className="w-4 h-4 border-2 border-stone-950/20 border-t-stone-950 rounded-full animate-spin"></span>
                  ) : (
                    <UserPlus className="w-4 h-4" />
                  )}
                  <span>Complete Verified Registration</span>
                </button>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
};


