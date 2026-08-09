# SavoryStay — Frontend UI (React + Vite + Tailwind CSS)

This folder contains the React frontend user interface for SavoryStay Culinary Operations & Pre-Booking System.

## Features
- **OTP Login & Authentication**: Mobile SMS & Email 6-digit OTP verification + Spring Security JWT auth.
- **Live Menu & Pre-Booking**: Realtime food menu browsing, category filtering, cart management, pickup / dine-in selection.
- **Realtime Payment Modal**: Instant UPI QR, Credit/Debit Card, Net Banking, and Cash on Delivery processing with live order confirmation.
- **Chef Operations Dashboard**: Interactive prep list management, live station status, pre-bookings timeline.

## Quick Start on Local Laptop

```bash
# 1. Navigate to the frontend directory
cd frontend

# 2. Install dependencies
npm install

# 3. Start local development server (runs on http://localhost:5173 or configured port)
npm run dev
```

The frontend will automatically proxy `/api/*` REST endpoints to your backend server running on `http://localhost:8080` (Spring Boot) or `http://localhost:3000` (Node.js).
