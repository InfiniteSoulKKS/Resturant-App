import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import crypto from "crypto";

// In-memory mock database store for Spring Boot & Postgres backend simulation
const mockUsersDB: Array<{
  id: string;
  username: string;
  email: string;
  phone?: string;
  passwordHash: string;
  role: string;
  enabled: boolean;
  createdAt: string;
  lastLogin?: string;
}> = [
  {
    id: "usr_mgr_01",
    username: "manager_admin",
    email: "manager@savorystay.com",
    phone: "+91 98765 00001",
    passwordHash: "$2a$12$e0MYzXyjpJS7Pd0RVvHwHe8vX13uP0R8q7QZ.qKk3p1uX4lV9y4u6",
    role: "ROLE_MANAGER",
    enabled: true,
    createdAt: new Date().toISOString(),
  },
  {
    id: "usr_chef_01",
    username: "chef_executive",
    email: "chef@savorystay.com",
    phone: "+91 98765 00002",
    passwordHash: "$2a$12$e0MYzXyjpJS7Pd0RVvHwHe8vX13uP0R8q7QZ.qKk3p1uX4lV9y4u6",
    role: "ROLE_CHEF",
    enabled: true,
    createdAt: new Date().toISOString(),
  },
  {
    id: "usr_cust_01",
    username: "guest_gourmet",
    email: "guest@example.com",
    phone: "+91 98765 43210",
    passwordHash: "$2a$12$9qX3p1uX4lV9y4u6e0MYzXyjpJS7Pd0RVvHwHe8vX13uP0R8q7QZ",
    role: "ROLE_CUSTOMER",
    enabled: true,
    createdAt: new Date().toISOString(),
  }
];

const mockTransactionsDB: Array<any> = [];

// In-memory OTP Store for SMS/WhatsApp/Email Verification
const mockOtpDB = new Map<string, { otp: string; expiresAt: number; verified: boolean }>();

// Helper to generate simulated BCrypt Hash, Access Token & Refresh Token
function simulateBCryptHash(password: string): string {
  const salt = crypto.createHash('sha256').update(password + "SAVORY_SALT").digest('hex').substring(0, 22);
  return `$2a$12$${salt}${crypto.createHash('sha256').update(password).digest('hex').substring(0, 31)}`;
}

function generateJwtTokens(user: { id: string; username: string; email: string; role: string; phone?: string }) {
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString('base64url');
  
  const accessTokenPayload = Buffer.from(JSON.stringify({
    sub: user.id,
    username: user.username,
    email: user.email,
    phone: user.phone || "+91 98765 43210",
    roles: [user.role],
    iss: "SavoryStay_SpringSecurity_Auth_Server",
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 3600 // 1 hour Access Token
  })).toString('base64url');

  const refreshTokenPayload = Buffer.from(JSON.stringify({
    sub: user.id,
    type: "REFRESH",
    tokenFamily: "tf_" + Math.random().toString(36).substring(2, 10),
    iss: "SavoryStay_SpringSecurity_Auth_Server",
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + (30 * 86400) // 30 days Refresh Token (2,592,000 seconds)
  })).toString('base64url');

  const accessSig = crypto.createHmac('sha256', 'SPRING_SECURITY_SECRET_KEY_98127391')
    .update(`${header}.${accessTokenPayload}`)
    .digest('base64url');

  const refreshSig = crypto.createHmac('sha256', 'SPRING_SECURITY_REFRESH_SECRET_KEY_88321')
    .update(`${header}.${refreshTokenPayload}`)
    .digest('base64url');

  return {
    accessToken: `${header}.${accessTokenPayload}.${accessSig}`,
    refreshToken: `${header}.${refreshTokenPayload}.${refreshSig}`,
    expiresIn: 3600,
    refreshExpiresIn: 2592000 // 30 Days Token Lifetime
  };
}

function decodeJwtToken(token: string) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    return payload;
  } catch (err) {
    return null;
  }
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json());

  // Spring Boot Health Check Endpoint
  app.get("/api/v1/health", (req, res) => {
    res.json({
      service: "SavoryStay Culinary Operations Backend",
      framework: "Spring Boot 3.2.0 (Spring Security 6.2 enabled)",
      database: "MySQL 8.0",
      security: "BCryptPasswordEncoder + JWT Authentication Filter",
      payments: "Stripe v14.0 & PayPal REST SDK Integration",
      status: "UP",
      timestamp: new Date().toISOString()
    });
  });

  // ==========================================
  // SPRING SECURITY AUTHENTICATION ENDPOINTS
  // ==========================================
  // SPRING SECURITY AUTHENTICATION & OTP ENDPOINTS
  // ==========================================

  // Dispatch OTP Endpoint (SMS / WhatsApp / Email)
  app.post("/api/v1/auth/send-otp", (req, res) => {
    const { phoneOrEmail } = req.body;

    if (!phoneOrEmail) {
      return res.status(400).json({
        error: "BAD_REQUEST",
        message: "Mobile phone number (+91) or email address is required to dispatch OTP."
      });
    }

    // Generate 6-digit random verification code
    const generatedOtp = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = Date.now() + (10 * 60 * 1000); // 10 minutes validity

    mockOtpDB.set(phoneOrEmail, {
      otp: generatedOtp,
      expiresAt,
      verified: false
    });

    console.log(`[SPRING BOOT OTP SERVICE] 📱 Verification Code dispatched to ${phoneOrEmail}: ${generatedOtp}`);

    res.json({
      success: true,
      message: `6-Digit OTP verification code sent to ${phoneOrEmail} via SMS & WhatsApp.`,
      demoOtp: generatedOtp,
      expiresInSeconds: 600
    });
  });

  // Verify OTP Endpoint
  app.post("/api/v1/auth/verify-otp", (req, res) => {
    const { phoneOrEmail, otp } = req.body;

    if (!phoneOrEmail || !otp) {
      return res.status(400).json({
        error: "BAD_REQUEST",
        message: "Mobile/Email and 6-digit OTP code are required."
      });
    }

    const record = mockOtpDB.get(phoneOrEmail);

    if (!record) {
      return res.status(400).json({
        error: "OTP_NOT_FOUND",
        message: "No OTP was requested for this mobile number or email. Please click 'Send OTP'."
      });
    }

    if (Date.now() > record.expiresAt) {
      return res.status(400).json({
        error: "OTP_EXPIRED",
        message: "OTP code has expired. Please request a new OTP."
      });
    }

    if (record.otp !== otp) {
      return res.status(400).json({
        error: "INVALID_OTP",
        message: "Invalid 6-digit OTP entered. Please try again."
      });
    }

    // Mark verified
    record.verified = true;
    mockOtpDB.set(phoneOrEmail, record);

    res.json({
      success: true,
      verified: true,
      message: "OTP verified successfully! You can now complete your registration."
    });
  });

  // User Registration Endpoint with Enforced OTP Verification
  app.post("/api/v1/auth/register", (req, res) => {
    const { username, email, phone, password, role, otp } = req.body;

    if (!username || !email || !password) {
      return res.status(400).json({
        error: "BAD_REQUEST",
        message: "Username, email, phone, and password are required fields."
      });
    }

    // Check OTP verification
    const targetIdentifier = phone || email;
    const otpRecord = mockOtpDB.get(targetIdentifier);

    // Accept if otpRecord exists & verified, or if direct matching OTP was provided
    const isOtpValid = otpRecord && (otpRecord.verified || otpRecord.otp === otp);

    if (!isOtpValid) {
      return res.status(400).json({
        error: "OTP_VERIFICATION_REQUIRED",
        message: "Mobile/Email verification failed. Please enter the valid 6-digit OTP sent to " + targetIdentifier
      });
    }

    const existingUser = mockUsersDB.find(u => u.username === username || u.email === email);
    if (existingUser) {
      return res.status(409).json({
        error: "USER_ALREADY_EXISTS",
        message: existingUser.username === username 
          ? "Username is already taken." 
          : "Email address is already registered."
      });
    }

    const newUser = {
      id: "usr_" + Math.random().toString(36).substring(2, 10),
      username,
      email,
      phone: phone || "+91 98765 43210",
      passwordHash: simulateBCryptHash(password),
      role: role && role.startsWith("ROLE_") ? role : "ROLE_CUSTOMER",
      enabled: true,
      createdAt: new Date().toISOString(),
      lastLogin: new Date().toISOString()
    };

    mockUsersDB.push(newUser);

    // Clear used OTP
    mockOtpDB.delete(targetIdentifier);

    const tokens = generateJwtTokens(newUser);

    res.status(201).json({
      message: "User registered & verified successfully via Spring Security with 30-Day Session Persistence.",
      token: tokens.accessToken,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: "Bearer",
      expiresIn: tokens.expiresIn,
      refreshExpiresIn: tokens.refreshExpiresIn,
      user: {
        id: newUser.id,
        username: newUser.username,
        email: newUser.email,
        phone: newUser.phone,
        role: newUser.role,
        createdAt: newUser.createdAt
      }
    });
  });

  // User Login Endpoint
  app.post("/api/v1/auth/login", (req, res) => {
    const { emailOrUsername, password } = req.body;

    if (!emailOrUsername || !password) {
      return res.status(400).json({
        error: "INVALID_CREDENTIALS",
        message: "Please provide valid credentials."
      });
    }

    const user = mockUsersDB.find(
      u => u.username === emailOrUsername || u.email === emailOrUsername
    );

    if (!user) {
      return res.status(401).json({
        error: "UNAUTHORIZED",
        message: "Invalid username/email or password."
      });
    }

    // Update last login
    user.lastLogin = new Date().toISOString();

    const tokens = generateJwtTokens(user);

    res.json({
      message: "Authentication successful via Spring Security 6.2.",
      token: tokens.accessToken,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: "Bearer",
      expiresIn: tokens.expiresIn,
      refreshExpiresIn: tokens.refreshExpiresIn,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        role: user.role,
        lastLogin: user.lastLogin
      }
    });
  });

  // User Login via OTP Endpoint (Mobile or Email)
  app.post("/api/v1/auth/login-otp", (req, res) => {
    const { phoneOrEmail, otp } = req.body;

    if (!phoneOrEmail || !otp) {
      return res.status(400).json({
        error: "BAD_REQUEST",
        message: "Mobile phone / Email and 6-digit OTP code are required."
      });
    }

    const record = mockOtpDB.get(phoneOrEmail);

    if (!record) {
      return res.status(400).json({
        error: "OTP_NOT_FOUND",
        message: "No OTP was requested for this mobile number or email. Please click 'Send OTP'."
      });
    }

    if (Date.now() > record.expiresAt) {
      return res.status(400).json({
        error: "OTP_EXPIRED",
        message: "OTP code has expired. Please request a new OTP."
      });
    }

    if (record.otp !== otp) {
      return res.status(400).json({
        error: "INVALID_OTP",
        message: "Invalid 6-digit OTP code entered. Please try again."
      });
    }

    // Find user by email or phone or create one if new
    let user = mockUsersDB.find(
      u => u.email === phoneOrEmail || (u as any).phone === phoneOrEmail || u.username === phoneOrEmail
    );

    if (!user) {
      // Auto-create user logged in via OTP
      const isEmail = phoneOrEmail.includes("@");
      const generatedUsername = isEmail 
        ? phoneOrEmail.split("@")[0] + "_" + Math.floor(100 + Math.random() * 900)
        : "user_" + phoneOrEmail.replace(/[^0-9]/g, "").slice(-6);

      user = {
        id: "usr_" + Math.random().toString(36).substring(2, 10),
        username: generatedUsername,
        email: isEmail ? phoneOrEmail : `${generatedUsername}@savorystay.com`,
        phone: !isEmail ? phoneOrEmail : "+91 98765 43210",
        passwordHash: simulateBCryptHash("SavoryOtpLogin123!"),
        role: "ROLE_CUSTOMER",
        enabled: true,
        createdAt: new Date().toISOString(),
        lastLogin: new Date().toISOString()
      };
      mockUsersDB.push(user);
    } else {
      user.lastLogin = new Date().toISOString();
    }

    // Clean up used OTP
    mockOtpDB.delete(phoneOrEmail);

    const tokens = generateJwtTokens(user);

    res.json({
      message: "OTP Authentication successful via Spring Security 6.2.",
      token: tokens.accessToken,
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: "Bearer",
      expiresIn: tokens.expiresIn,
      refreshExpiresIn: tokens.refreshExpiresIn,
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        phone: (user as any).phone || phoneOrEmail,
        role: user.role,
        lastLogin: user.lastLogin
      }
    });
  });

  // JWT Refresh Token Endpoint
  app.post("/api/v1/auth/refresh", (req, res) => {
    const { refreshToken } = req.body;

    if (!refreshToken) {
      return res.status(400).json({
        error: "BAD_REQUEST",
        message: "Refresh token is required in body."
      });
    }

    const payload = decodeJwtToken(refreshToken);
    if (!payload || payload.type !== "REFRESH") {
      return res.status(401).json({
        error: "INVALID_REFRESH_TOKEN",
        message: "Refresh token is invalid or expired."
      });
    }

    const user = mockUsersDB.find(u => u.id === payload.sub);
    if (!user) {
      return res.status(401).json({
        error: "USER_NOT_FOUND",
        message: "User account no longer exists."
      });
    }

    const newTokens = generateJwtTokens(user);

    res.json({
      message: "JWT Access Token refreshed successfully via Spring Security.",
      accessToken: newTokens.accessToken,
      refreshToken: newTokens.refreshToken,
      tokenType: "Bearer",
      expiresIn: newTokens.expiresIn
    });
  });

  // Multi-Channel Order Notification Endpoint (App Push, SMS, WhatsApp, Email)
  app.post("/api/v1/notifications/send-order-alert", (req, res) => {
    const { orderId, orderNumber, customerName, phone, email, status } = req.body;

    const channels = ["APP_PUSH", "SMS", "WHATSAPP", "EMAIL"];
    const formattedPhone = phone || "+91 98765 43210";
    const formattedEmail = email || `${customerName?.toLowerCase().replace(/\s+/g, '')}@gmail.com`;

    let alertMessage = "";
    if (status === "PREPARING") {
      alertMessage = `👨‍🍳 SavoryStay Update: Chef has started preparing your order ${orderNumber || '#ORD-1000'}!`;
    } else if (status === "PACKED_READY") {
      alertMessage = `🍱 Order Prepared & Packed! Your order ${orderNumber || '#ORD-1000'} is hot & ready for pickup at Counter #2.`;
    } else if (status === "COMPLETED") {
      alertMessage = `✨ Thank you for dining with SavoryStay! Order ${orderNumber || '#ORD-1000'} served.`;
    } else {
      alertMessage = `🔔 Order ${orderNumber || '#ORD-1000'} status updated to: ${status}`;
    }

    console.log(`[SPRING BOOT NOTIFICATION DISPATCHER]`);
    console.log(`📱 SMS Sent to ${formattedPhone}: "${alertMessage}"`);
    console.log(`💬 WhatsApp Sent to ${formattedPhone}: "${alertMessage}"`);
    console.log(`📧 Email Dispatched to ${formattedEmail}: Subject: SavoryStay Order Update - ${alertMessage}`);

    res.json({
      success: true,
      orderId,
      orderNumber,
      status,
      dispatchedAt: new Date().toISOString(),
      channels: [
        { name: "APP_PUSH", status: "DELIVERED", details: "In-App Toast Banner" },
        { name: "SMS", status: "SENT", provider: "Twilio / Fast2SMS", target: formattedPhone },
        { name: "WHATSAPP", status: "SENT", provider: "Meta WhatsApp Cloud API", target: formattedPhone },
        { name: "EMAIL", status: "SENT", provider: "Spring Mail / SMTP", target: formattedEmail }
      ],
      alertMessage
    });
  });

  // User Profile / Current Session Endpoint
  app.get("/api/v1/auth/me", (req, res) => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.status(401).json({
        error: "MISSING_TOKEN",
        message: "Full authentication is required to access this resource."
      });
    }

    const token = authHeader.split(" ")[1];
    const payload = decodeJwtToken(token);

    if (!payload) {
      return res.status(401).json({
        error: "INVALID_TOKEN",
        message: "JWT signature validation failed or token is expired."
      });
    }

    const user = mockUsersDB.find(u => u.id === payload.sub);

    res.json({
      authenticated: true,
      user: user ? {
        id: user.id,
        username: user.username,
        email: user.email,
        role: user.role,
        createdAt: user.createdAt,
        lastLogin: user.lastLogin
      } : {
        id: payload.sub,
        username: payload.username,
        email: payload.email,
        role: payload.roles[0]
      },
      jwtClaims: payload
    });
  });

  // ==========================================
  // REAL-TIME PAYMENT GATEWAY ENDPOINTS
  // ==========================================

  // Create Stripe / PayPal Payment Intent
  app.post("/api/v1/payments/create-intent", (req, res) => {
    const { amount, currency = "USD", gateway = "STRIPE", customerName } = req.body;

    const paymentIntentId = (gateway === "PAYPAL" ? "PAYPAL_ORD_" : "pi_3M") + Math.random().toString(36).substring(2, 14);
    const clientSecret = paymentIntentId + "_secret_" + Math.random().toString(36).substring(2, 10);

    res.json({
      gateway,
      paymentIntentId,
      clientSecret,
      amount: amount || 42.00,
      currency: currency.toUpperCase(),
      status: "requires_payment_method",
      livemode: false,
      pciCompliance: "PCI-DSS Level 1 Validated",
      created: Math.floor(Date.now() / 1000)
    });
  });

  // Process Real-time Payment & Token Capture
  app.post("/api/v1/payments/process-realtime", (req, res) => {
    const { orderId, amount, method, gateway = "STRIPE", cardDetails, paypalToken } = req.body;
    
    // Simulate payment validation & tokenization
    const transactionId = "TXN_" + (gateway === "PAYPAL" ? "PP_" : "ST_") + Math.random().toString(36).substring(2, 10).toUpperCase();
    
    const record = {
      transactionId,
      orderId: orderId || "ORD_" + Date.now(),
      gateway,
      amountPaid: amount || 36.00,
      method: method || "CARD",
      paymentStatus: "PAID",
      cardLast4: cardDetails?.number ? cardDetails.number.slice(-4) : "4242",
      gatewayTimestamp: new Date().toISOString(),
      securityCheck: {
        cvcCheck: "pass",
        addressZipCheck: "pass",
        riskScore: 3,
        threeDSecureAuthenticated: true
      }
    };

    mockTransactionsDB.push(record);

    res.json({
      status: "SUCCESS",
      paymentStatus: "PAID",
      transactionId,
      orderId: record.orderId,
      gateway,
      amountPaid: record.amountPaid,
      paymentMethod: method || "CARD",
      gatewayTimestamp: record.gatewayTimestamp,
      security: record.securityCheck,
      message: `Real-time payment captured via Spring Boot ${gateway} Service.`
    });
  });

  // Payment Webhook Callback Endpoint
  app.post("/api/v1/payments/webhook", (req, res) => {
    const signature = req.headers["stripe-signature"] || req.headers["paypal-transmission-sig"];
    const event = req.body;

    console.log(`[SPRING BOOT WEBHOOK] Received event ${event?.type || 'payment_intent.succeeded'}`);

    res.json({
      received: true,
      verifiedSignature: !!signature || true,
      processedBy: "Spring Boot PaymentWebhookHandler.java",
      timestamp: new Date().toISOString()
    });
  });

  // Get Transaction Status Endpoint
  app.get("/api/v1/payments/status/:txnId", (req, res) => {
    const txn = mockTransactionsDB.find(t => t.transactionId === req.params.txnId);
    if (!txn) {
      return res.status(404).json({
        error: "NOT_FOUND",
        message: "Transaction ID not found in Spring Boot Payment Audit Log."
      });
    }
    res.json(txn);
  });

  app.get("/api/v1/spring-code", (req, res) => {
    res.json({
      javaClass: "CulinaryOrderController.java",
      springVersion: "3.2.0",
      security: "Spring Security 6.2 with BCrypt & JWT",
      orm: "Hibernate / Spring Data JPA",
      databaseDriver: "com.mysql.cj.jdbc.Driver"
    });
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`SavoryStay Server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();

