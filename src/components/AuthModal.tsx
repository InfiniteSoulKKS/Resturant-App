import React, { useEffect, useRef, useState } from 'react';
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
  Send,
} from 'lucide-react';
import * as api from '../lib/apiClient';
import { storeToken, removeToken } from '../lib/tokenManager';
import { formatRoles } from '../lib/roles';

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentUser: any;
  onLoginSuccess: (user: any, token: string) => void;
  onLogout: () => void;
  /** Optional context shown to the user, e.g. "Please sign in to continue with your order." */
  promptMessage?: string;
}

export const AuthModal: React.FC<AuthModalProps> = ({
  isOpen,
  onClose,
  currentUser,
  onLoginSuccess,
  onLogout,
  promptMessage,
}) => {
  const [authMode, setAuthMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');
  const [loginMethod, setLoginMethod] = useState<'OTP' | 'PASSWORD'>('OTP');

  // Login OTP State
  const [loginUsername, setLoginUsername] = useState('');
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPhone, setLoginPhone] = useState('');
  const [loginOtpChannel, setLoginOtpChannel] = useState<'EMAIL' | 'SMS' | 'WHATSAPP'>('EMAIL');
  const [loginOtpSent, setLoginOtpSent] = useState(false);
  const [loginOtpCode, setLoginOtpCode] = useState('');

  // Login Password State
  const [loginIdentifier, setLoginIdentifier] = useState('');
  const [loginPassword, setLoginPassword] = useState('');

  // Register State
  const [regUsername, setRegUsername] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regOtpChannel, setRegOtpChannel] = useState<'EMAIL' | 'SMS' | 'WHATSAPP'>('EMAIL');
  const [regOtpSent, setRegOtpSent] = useState(false);
  const [regOtpCode, setRegOtpCode] = useState('');
  const [isRegOtpVerified, setIsRegOtpVerified] = useState(false);
  const [alreadyExists, setAlreadyExists] = useState(false);
  // Early availability warnings from GET /auth/check-availability (advisory only).
  const [availability, setAvailability] = useState({
    usernameTaken: false,
    emailTaken: false,
    phoneTaken: false,
  });
  // Guards against stale debounced responses overwriting newer ones.
  const availabilityRequestId = useRef(0);

  // UI State
  const [isLoading, setIsLoading] = useState(false);
  const [isOtpLoading, setIsOtpLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Debounced pre-registration availability check: warn the user as they type
  // if the username/email/phone is already taken, BEFORE they spend an OTP.
  useEffect(() => {
    const id = ++availabilityRequestId.current;
    const t = setTimeout(async () => {
      const payload: { username?: string; email?: string; phone?: string } = {};
      if (regUsername.trim()) payload.username = regUsername.trim();
      if (regEmail.trim()) payload.email = regEmail.trim();
      if (regPhone.trim()) payload.phone = regPhone.trim();
      if (Object.keys(payload).length === 0) {
        setAvailability({ usernameTaken: false, emailTaken: false, phoneTaken: false });
        return;
      }
      try {
        const result = await api.checkAvailability(payload);
        // Ignore stale responses — only the latest request may update state.
        if (availabilityRequestId.current === id) setAvailability(result);
      } catch {
        // Fail open on network errors so the button is never stuck disabled;
        // the backend stays authoritative at submit time.
        if (availabilityRequestId.current === id) {
          setAvailability({ usernameTaken: false, emailTaken: false, phoneTaken: false });
        }
      }
    }, 450);
    return () => clearTimeout(t);
  }, [regUsername, regEmail, regPhone]);

  // Reset all form fields when the modal closes so stale data doesn't
  // persist across logout → login cycles.
  useEffect(() => {
    if (!isOpen) {
      // Login state
      setLoginUsername('');
      setLoginEmail('');
      setLoginPhone('');
      setLoginOtpSent(false);
      setLoginOtpCode('');
      setLoginIdentifier('');
      setLoginPassword('');
      // Register state
      setRegUsername('');
      setRegEmail('');
      setRegPhone('');
      setRegPassword('');
      setRegOtpSent(false);
      setRegOtpCode('');
      setIsRegOtpVerified(false);
      setAlreadyExists(false);
      // UI state
      setIsLoading(false);
      setIsOtpLoading(false);
      setErrorMessage(null);
      setSuccessMessage(null);
      // Reset to login mode
      setAuthMode('LOGIN');
      setLoginMethod('OTP');
    }
  }, [isOpen]);

  if (!isOpen) return null;

  // ============ LOGIN OTP HANDLERS ============
  const handleSendLoginOtp = async () => {
    if (!loginUsername) {
      setErrorMessage("Please enter your username.");
      return;
    }

    if (loginOtpChannel === 'EMAIL' && !loginEmail) {
      setErrorMessage("Please enter your email for OTP.");
      return;
    }

    if ((loginOtpChannel === 'SMS' || loginOtpChannel === 'WHATSAPP') && !loginPhone) {
      setErrorMessage("Please enter your phone number for OTP.");
      return;
    }

    setIsOtpLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      // Login OTPs are only issued to accounts that exist — check the username
      // first so an invalid user doesn't get an OTP (and a confusing later error).
      const availability = await api.checkAvailability({ username: loginUsername.trim() });
      if (!availability.usernameTaken) {
        setErrorMessage("No account found with this username. Check the spelling or sign up instead.");
        return;
      }

      let response: api.OtpResponse;
      if (loginOtpChannel === 'EMAIL') {
        response = await api.sendOtpEmail(loginEmail, loginUsername.trim());
      } else if (loginOtpChannel === 'SMS') {
        response = await api.sendOtpSms(loginPhone, loginUsername.trim());
      } else {
        response = await api.sendOtpWhatsApp(loginPhone, loginUsername.trim());
      }

      setLoginOtpSent(true);
      if (response.demoOtp) {
        // Demo mode: backend returned the code because no SMS/email provider is configured.
        setLoginOtpCode(response.demoOtp);
        setSuccessMessage(`🧪 Demo mode — your ${loginOtpChannel} OTP is ${response.demoOtp} (autofilled)`);
      } else {
        setSuccessMessage(`✅ OTP sent via ${loginOtpChannel}! Check your ${loginOtpChannel === 'EMAIL' ? 'email' : 'phone'}.`);
      }
    } catch (err: any) {
      setErrorMessage(err.message || `Failed to send ${loginOtpChannel} OTP.`);
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
      const response = await api.loginWithOtp({
        username: loginUsername,
        otpCode: loginOtpCode,
        channel: loginOtpChannel,
        deliveryTarget: loginOtpChannel === 'EMAIL' ? loginEmail : loginPhone,
      });

      storeToken(response.token);
      onLoginSuccess(response.user, response.token);

      setSuccessMessage(`🎉 Welcome back, ${response.user.username}!`);
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      setErrorMessage(err.message || 'OTP verification failed.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!loginIdentifier || !loginPassword) {
      setErrorMessage("Please enter username and password.");
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const response = await api.loginWithPassword({
        username: loginIdentifier,
        password: loginPassword,
      });

      storeToken(response.token);
      onLoginSuccess(response.user, response.token);

      setSuccessMessage(`🎉 Welcome back, ${response.user.username}!`);
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      setErrorMessage(err.message || 'Login failed.');
    } finally {
      setIsLoading(false);
    }
  };

  // ============ REGISTRATION HANDLERS ============
  const handleSendRegistrationOtp = async () => {
    if (!regEmail && !regPhone) {
      setErrorMessage("Please enter an email or phone number.");
      return;
    }

    if (regOtpChannel === 'EMAIL' && !regEmail) {
      setErrorMessage("Please enter your email for OTP.");
      return;
    }

    if ((regOtpChannel === 'SMS' || regOtpChannel === 'WHATSAPP') && !regPhone) {
      setErrorMessage("Please enter your phone number for OTP.");
      return;
    }

    setIsOtpLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      let response: api.OtpResponse;
      if (regOtpChannel === 'EMAIL') {
        response = await api.sendOtpEmail(regEmail);
      } else if (regOtpChannel === 'SMS') {
        response = await api.sendOtpSms(regPhone);
      } else {
        response = await api.sendOtpWhatsApp(regPhone);
      }

      setRegOtpSent(true);
      if (response.demoOtp) {
        // Demo mode: backend returned the code because no SMS/email provider is configured.
        setRegOtpCode(response.demoOtp);
        setSuccessMessage(`🧪 Demo mode — your ${regOtpChannel} OTP is ${response.demoOtp} (autofilled)`);
      } else {
        setSuccessMessage(`✅ OTP sent via ${regOtpChannel}!`);
      }
    } catch (err: any) {
      setErrorMessage(err.message || `Failed to send ${regOtpChannel} OTP.`);
    } finally {
      setIsOtpLoading(false);
    }
  };

  const handleVerifyRegistrationOtp = async () => {
    if (!regOtpCode) {
      setErrorMessage("Please enter the OTP code.");
      return;
    }

    setIsOtpLoading(true);
    setErrorMessage(null);

    // The backend stores the OTP keyed by the delivery target (email or phone),
    // so pass the same identifier used when sending.
    const verifyId = regOtpChannel === 'EMAIL' ? regEmail : regPhone;
    try {
      await api.verifyOtp({
        userId: verifyId,
        otpCode: regOtpCode,
        channel: regOtpChannel,
      });

      setIsRegOtpVerified(true);
      setSuccessMessage("✅ OTP verified! Complete your registration below.");
    } catch (err: any) {
      setErrorMessage(err.message || 'Invalid OTP code.');
    } finally {
      setIsOtpLoading(false);
    }
  };

  /**
   * The OTP on the backend is keyed to the exact email/phone it was sent to.
   * If the user edits their contact after sending/verifying an OTP, the old
   * code is meaningless for the new address — reset the OTP state so they must
   * send and verify a fresh code instead of hitting a confusing backend error.
   */
  const handleRegContactChanged = () => {
    if (regOtpSent || isRegOtpVerified) {
      setIsRegOtpVerified(false);
      setRegOtpSent(false);
      setRegOtpCode('');
      setSuccessMessage(null);
      setErrorMessage('Contact changed — a new OTP is required for the updated email/phone.');
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!regUsername || !regEmail || !regPassword) {
      setErrorMessage("Please fill username, email, and password.");
      return;
    }

    if (!isRegOtpVerified) {
      setErrorMessage("Please verify OTP before registering.");
      return;
    }

    // Belt-and-suspenders: block submit if the contact is already taken (the
    // backend re-checks authoritatively too, but avoid wasting an OTP).
    if (
      (regOtpChannel === 'EMAIL' && availability.emailTaken) ||
      ((regOtpChannel === 'SMS' || regOtpChannel === 'WHATSAPP') && availability.phoneTaken)
    ) {
      setErrorMessage("This contact is already registered. Please sign in instead.");
      setAlreadyExists(true);
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const response = await api.registerUser({
        username: regUsername,
        email: regEmail,
        password: regPassword,
        phone: regPhone || undefined,
        otpCode: regOtpCode,
        otpChannel: regOtpChannel,
      });

      setAlreadyExists(false);
      storeToken(response.token);
      onLoginSuccess(response.user, response.token);

      setSuccessMessage(`🎉 Registration successful! Welcome, ${response.user.username}!`);
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      const msg = err.message || 'Registration failed.';
      setErrorMessage(msg);
      // The account already exists (email/username/phone in use) — point the
      // user at signing in instead of leaving them stuck on the form.
      setAlreadyExists(/already (registered|in use|taken)|already exists/i.test(msg));
    } finally {
      setIsLoading(false);
    }
  };

  const handleLogout = () => {
    removeToken();
    onLogout();
  };

  /** Jumps to the Sign In tab, pre-filling the identifier with the offending contact. */
  const switchToSignIn = (prefill?: string) => {
    setAuthMode('LOGIN');
    setAlreadyExists(false);
    if (prefill) setLoginIdentifier(prefill);
    setErrorMessage(null);
    setSuccessMessage(null);
  };

  // ============ JSX RENDERING ============
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-stone-950/80 backdrop-blur-md overflow-y-auto">
      <div className="bg-stone-900/95 border border-stone-800 rounded-3xl max-w-2xl w-full p-6 md:p-8 shadow-2xl relative my-8 text-stone-100">
        {/* Header */}
        <div className="flex justify-between items-center pb-4 border-b border-stone-800">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 bg-amber-500/10 border border-amber-500/20 rounded-2xl flex items-center justify-center text-amber-400">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold font-serif text-stone-100">Authentication</h3>
              <p className="text-[10px] font-mono text-emerald-400">Spring Boot Backend • JWT Auth</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-stone-400 hover:text-stone-100 p-1.5 rounded-xl hover:bg-stone-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Messages */}
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

        {promptMessage && !currentUser && (
          <div className="mt-4 p-3 bg-amber-500/10 border border-amber-500/30 rounded-xl text-xs text-amber-400 flex items-center gap-2">
            <Lock className="w-4 h-4 shrink-0" />
            <span>{promptMessage}</span>
          </div>
        )}

        {/* When registration fails because the account already exists, offer an escape hatch into login. */}
        {alreadyExists && (
          <button
            type="button"
            onClick={() => switchToSignIn(regEmail)}
            className="mt-2 w-full py-2 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 text-xs font-bold rounded-xl border border-amber-500/30 transition-colors cursor-pointer flex items-center justify-center gap-2"
          >
            This account already exists — Sign in instead →
          </button>
        )}

        {/* LOGGED IN STATE */}
        {currentUser ? (
          <div className="py-6 space-y-5">
            <div className="bg-stone-950 p-4 rounded-2xl border border-stone-800 space-y-3">
              <div className="flex justify-between items-center pb-3 border-b border-stone-800">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 bg-amber-500 text-stone-950 font-bold rounded-xl flex items-center justify-center uppercase shadow-md shadow-amber-500/20">
                    {currentUser.username ? currentUser.username.charAt(0) : 'U'}
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-stone-100">{currentUser.username}</h4>
                    <p className="text-xs text-stone-400">{currentUser.email}</p>
                  </div>
                </div>
                <span className="text-[10px] font-mono font-bold px-2.5 py-1 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/30">
                  {formatRoles(currentUser.role)}
                </span>
              </div>
            </div>

            <button
              type="button"
              onClick={handleLogout}
              className="w-full py-2.5 bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 text-xs font-bold rounded-xl border border-rose-500/30 transition-colors cursor-pointer flex items-center justify-center gap-2"
            >
              <LogOut className="w-4 h-4" />
              <span>Sign Out</span>
            </button>
          </div>
        ) : (
          /* LOGIN / REGISTER TABS */
          <div className="pt-4 space-y-4">
            {/* Mode Switcher */}
            <div className="grid grid-cols-2 p-1 bg-stone-950 rounded-2xl border border-stone-800 text-xs font-medium">
              <button
                type="button"
                onClick={() => {
                  setAuthMode('LOGIN');
                  setErrorMessage(null);
                  setAlreadyExists(false);
                }}
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
                onClick={() => {
                  setAuthMode('REGISTER');
                  setErrorMessage(null);
                  setAlreadyExists(false);
                }}
                className={`py-2 rounded-xl transition-all cursor-pointer ${
                  authMode === 'REGISTER'
                    ? 'bg-amber-500 text-stone-950 font-bold shadow'
                    : 'text-stone-400 hover:text-stone-100'
                }`}
              >
                Sign Up
              </button>
            </div>

            {/* LOGIN MODE */}
            {authMode === 'LOGIN' && (
              <div className="space-y-4">
                {/* Login Method Switcher */}
                <div className="grid grid-cols-2 p-1 bg-stone-900 rounded-xl border border-stone-800 text-xs font-medium">
                  <button
                    type="button"
                    onClick={() => {
                      setLoginMethod('OTP');
                      setErrorMessage(null);
                    }}
                    className={`py-1.5 rounded-lg transition-all cursor-pointer ${
                      loginMethod === 'OTP'
                        ? 'bg-emerald-500/20 text-emerald-400 font-bold'
                        : 'text-stone-400 hover:text-stone-100'
                    }`}
                  >
                    OTP Login
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setLoginMethod('PASSWORD');
                      setErrorMessage(null);
                    }}
                    className={`py-1.5 rounded-lg transition-all cursor-pointer ${
                      loginMethod === 'PASSWORD'
                        ? 'bg-emerald-500/20 text-emerald-400 font-bold'
                        : 'text-stone-400 hover:text-stone-100'
                    }`}
                  >
                    Password Login
                  </button>
                </div>

                {/* OTP LOGIN */}
                {loginMethod === 'OTP' && (
                  <form onSubmit={handleLoginWithOtpSubmit} className="space-y-3">
                    <input
                      type="text"
                      placeholder="Username"
                      value={loginUsername}
                      onChange={(e) => setLoginUsername(e.target.value)}
                      className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                    />

                    {/* OTP Channel Selector */}
                    <div className="grid grid-cols-3 gap-2">
                      {(['EMAIL', 'SMS', 'WHATSAPP'] as const).map((channel) => (
                        <button
                          key={channel}
                          type="button"
                          onClick={() => setLoginOtpChannel(channel)}
                          className={`py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                            loginOtpChannel === channel
                              ? 'bg-amber-500 text-stone-950'
                              : 'bg-stone-800 text-stone-300 hover:bg-stone-700'
                          }`}
                        >
                          {channel}
                        </button>
                      ))}
                    </div>

                    {/* Email Input */}
                    {loginOtpChannel === 'EMAIL' && (
                      <input
                        type="email"
                        placeholder="Your email"
                        value={loginEmail}
                        onChange={(e) => setLoginEmail(e.target.value)}
                        className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                      />
                    )}

                    {/* Phone Input */}
                    {(loginOtpChannel === 'SMS' || loginOtpChannel === 'WHATSAPP') && (
                      <input
                        type="tel"
                        placeholder="Phone (+919876543210)"
                        value={loginPhone}
                        onChange={(e) => setLoginPhone(e.target.value)}
                        className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                      />
                    )}

                    {!loginOtpSent ? (
                      <button
                        type="button"
                        onClick={handleSendLoginOtp}
                        disabled={isOtpLoading}
                        className="w-full py-2 bg-amber-500 hover:bg-amber-600 text-stone-950 text-xs font-bold rounded-lg transition-all disabled:opacity-50 flex items-center justify-center gap-2"
                      >
                        <Send className="w-3.5 h-3.5" />
                        {isOtpLoading ? 'Sending...' : 'Send OTP'}
                      </button>
                    ) : (
                      <>
                        <input
                          type="text"
                          placeholder="6-digit OTP code"
                          maxLength={6}
                          value={loginOtpCode}
                          onChange={(e) => setLoginOtpCode(e.target.value.replace(/\D/g, ''))}
                          className="w-full px-3 py-2 bg-stone-800 border border-emerald-500/30 rounded-lg text-xs text-center text-stone-100 placeholder-stone-500 focus:outline-none focus:border-emerald-500 tracking-widest"
                        />
                        <button
                          type="submit"
                          disabled={isLoading || !loginOtpCode}
                          className="w-full py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-lg transition-all disabled:opacity-50"
                        >
                          {isLoading ? 'Verifying...' : 'Verify & Login'}
                        </button>
                      </>
                    )}
                  </form>
                )}

                {/* PASSWORD LOGIN */}
                {loginMethod === 'PASSWORD' && (
                  <form onSubmit={handleLoginSubmit} className="space-y-3">
                    <input
                      type="text"
                      placeholder="Username or Email"
                      value={loginIdentifier}
                      onChange={(e) => setLoginIdentifier(e.target.value)}
                      className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                    />
                    <input
                      type="password"
                      placeholder="Password"
                      value={loginPassword}
                      onChange={(e) => setLoginPassword(e.target.value)}
                      className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                    />
                    <button
                      type="submit"
                      disabled={isLoading}
                      className="w-full py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-lg transition-all disabled:opacity-50"
                    >
                      {isLoading ? 'Logging in...' : 'Login'}
                    </button>
                  </form>
                )}
              </div>
            )}

            {/* REGISTER MODE */}
            {authMode === 'REGISTER' && (
              <form onSubmit={handleRegisterSubmit} className="space-y-3">
                <input
                  type="text"
                  placeholder="Username"
                  value={regUsername}
                  onChange={(e) => setRegUsername(e.target.value)}
                  className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                />
                {availability.usernameTaken && (
                  <p className="text-[10px] text-rose-400 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 shrink-0" /> This username is already taken — try another.
                  </p>
                )}
                <input
                  type="email"
                  placeholder="Email"
                  value={regEmail}
                  onChange={(e) => {
                    setRegEmail(e.target.value);
                    handleRegContactChanged();
                  }}
                  className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                />
                {availability.emailTaken && (
                  <p className="text-[10px] text-rose-400 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 shrink-0" /> This email is already registered.{' '}
                    <button
                      type="button"
                      onClick={() => switchToSignIn(regEmail)}
                      className="underline font-bold hover:text-rose-300 cursor-pointer"
                    >
                      Sign in instead
                    </button>
                  </p>
                )}
                <input
                  type="tel"
                  placeholder="Phone (optional, e.g., +919876543210)"
                  value={regPhone}
                  onChange={(e) => {
                    setRegPhone(e.target.value);
                    handleRegContactChanged();
                  }}
                  className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                />
                {availability.phoneTaken && (
                  <p className="text-[10px] text-rose-400 flex items-center gap-1">
                    <AlertCircle className="w-3 h-3 shrink-0" /> This phone is already registered.
                  </p>
                )}
                <input
                  type="password"
                  placeholder="Password"
                  value={regPassword}
                  onChange={(e) => setRegPassword(e.target.value)}
                  className="w-full px-3 py-2 bg-stone-800 border border-stone-700 rounded-lg text-xs text-stone-100 placeholder-stone-500 focus:outline-none focus:border-amber-500"
                />

                {!isRegOtpVerified ? (
                  <>
                    {/* OTP Channel for Registration */}
                    <div className="grid grid-cols-3 gap-2">
                      {(['EMAIL', 'SMS', 'WHATSAPP'] as const).map((channel) => (
                        <button
                          key={channel}
                          type="button"
                          onClick={() => setRegOtpChannel(channel)}
                          className={`py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                            regOtpChannel === channel
                              ? 'bg-amber-500 text-stone-950'
                              : 'bg-stone-800 text-stone-300 hover:bg-stone-700'
                          }`}
                        >
                          {channel}
                        </button>
                      ))}
                    </div>

                    {!regOtpSent ? (
                      <button
                        type="button"
                        onClick={handleSendRegistrationOtp}
                        disabled={
                          isOtpLoading ||
                          (regOtpChannel === 'EMAIL' && availability.emailTaken) ||
                          ((regOtpChannel === 'SMS' || regOtpChannel === 'WHATSAPP') && availability.phoneTaken)
                        }
                        className="w-full py-2 bg-amber-500 hover:bg-amber-600 text-stone-950 text-xs font-bold rounded-lg transition-all disabled:opacity-50 flex items-center justify-center gap-2"
                      >
                        <Send className="w-3.5 h-3.5" />
                        {isOtpLoading ? 'Sending...' : 'Send OTP'}
                      </button>
                    ) : (
                      <>
                        <input
                          type="text"
                          placeholder="6-digit OTP code"
                          maxLength={6}
                          value={regOtpCode}
                          onChange={(e) => setRegOtpCode(e.target.value.replace(/\D/g, ''))}
                          className="w-full px-3 py-2 bg-stone-800 border border-emerald-500/30 rounded-lg text-xs text-center text-stone-100 placeholder-stone-500 focus:outline-none focus:border-emerald-500 tracking-widest"
                        />
                        <button
                          type="button"
                          onClick={handleVerifyRegistrationOtp}
                          disabled={isOtpLoading || !regOtpCode}
                          className="w-full py-2 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded-lg transition-all disabled:opacity-50"
                        >
                          {isOtpLoading ? 'Verifying...' : 'Verify OTP'}
                        </button>
                      </>
                    )}
                  </>
                ) : (
                  <div className="p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-lg text-xs text-emerald-400 font-bold flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    OTP Verified ✓
                  </div>
                )}

                <button
                  type="submit"
                  disabled={isLoading || !isRegOtpVerified}
                  className="w-full py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold rounded-lg transition-all disabled:opacity-50"
                >
                  {isLoading ? 'Registering...' : 'Complete Registration'}
                </button>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
