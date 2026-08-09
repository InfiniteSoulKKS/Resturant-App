# SavoryStay — Backend Services

This directory contains the backend services for SavoryStay.

## Directory Structure

```
backend/
├── springboot-backend/      # Spring Boot 3.2 Java Backend (Spring Security 6.2, JWT, JPA, REST APIs)
└── express-backend/         # Express.js / TypeScript REST API Backend with OTP, Firestore & Payment Processing
```

---

## 1. Spring Boot 3.2 Java Backend (`backend/springboot-backend/`)

### Prerequisites
- Java 17 or higher
- Maven 3.8+ (or use the included `./mvnw` wrapper)

### Running Locally
```bash
cd backend/springboot-backend

# Build & run Spring Boot application
./mvnw spring-boot:run
```
The Spring Boot REST APIs run on **`http://localhost:8080`**.

---

## 2. Express.js / Node Backend (`backend/express-backend/`)

### Prerequisites
- Node.js v18+

### Running Locally
```bash
cd backend/express-backend

# Install dependencies
npm install

# Start Express server
npm run dev
```
The Express backend server runs on **`http://localhost:3000`**.

### REST Endpoints Summary
- `POST /api/v1/auth/send-otp` : Generate & send 6-digit SMS/Email OTP
- `POST /api/v1/auth/login-otp` : Authenticate user via 6-digit OTP code
- `POST /api/v1/auth/login` : Authenticate user via Email/Username & Password
- `POST /api/v1/auth/refresh` : Refresh JWT Token
- `POST /api/v1/payments/process` : Realtime payment processing & order creation
- `GET /api/v1/health` : System health check
