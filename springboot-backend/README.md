# SavoryStay Culinary Operations - Standalone Spring Boot Backend

A production-grade, enterprise-ready **Spring Boot 3.2** backend application featuring **Spring Security JWT Authentication**, **PostgreSQL Spring Data JPA persistence**, and **Stripe / PayPal Real-Time Payment Gateway integrations**.

---

## 🚀 Key Features Included

1. **Spring Security 6.2 with JWT**:
   - BCrypt Password Encoder (12 Salt Rounds).
   - Stateless session management with custom `JwtAuthenticationFilter`.
   - Token creation (`JwtTokenProvider`) supporting User IDs & Custom Roles (`ROLE_CUSTOMER`, `ROLE_CHEF`, `ROLE_ADMIN`).
2. **Payment Gateway Integration**:
   - **Stripe Java SDK v24**: Payment Intent Creation & 3D Secure 2.0 Webhook Verification.
   - **PayPal Checkout SDK v2.0**: Express Checkout Order Creation & Capture.
3. **PostgreSQL Relational Storage**:
   - Entities for `User`, `MenuItem`, `Order`, `Payment`, and `OrderItem`.
   - Auto DDL Generation via Spring Data JPA + Hibernate.
4. **REST APIs & CORS Configuration**:
   - Webhook callback listener `/api/v1/payments/webhook`.
   - Pre-book & Real-time checkout endpoints `/api/v1/payments/process-realtime`.

---

## 📂 Folder Structure

```
springboot-backend/
├── pom.xml                                   # Maven Dependencies & Plugins
├── README.md                                  # Setup & Execution Guide
└── src/
    └── main/
        ├── java/
        │   └── com/savorystay/
        │       ├── SavoryStayApplication.java # Spring Boot Main Class
        │       ├── controller/
        │       │   ├── AuthController.java
        │       │   └── PaymentController.java
        │       ├── entity/
        │       │   ├── User.java
        │       │   ├── Order.java
        │       │   └── Payment.java
        │       ├── repository/
        │       │   ├── UserRepository.java
        │       │   ├── OrderRepository.java
        │       │   └── PaymentRepository.java
        │       ├── security/
        │       │   ├── JwtAuthenticationFilter.java
        │       │   ├── JwtTokenProvider.java
        │       │   └── SecurityConfig.java
        │       └── service/
        │           └── PaymentGatewayService.java
        └── resources/
            ├── application.yml                # Configuration & Secrets
            └── schema.sql                     # PostgreSQL DDL Script
```

---

## 🛠️ Requirements

- **Java Development Kit (JDK)**: Java 17 or Java 21.
- **Apache Maven**: 3.8+ (or use `./mvnw` wrapper).
- **PostgreSQL Database**: v14+ running locally or on Cloud SQL / Supabase.

---

## ⚙️ How to Import into Your IDE

### **IntelliJ IDEA**
1. Open IntelliJ IDEA -> Click **File** -> **Open...**
2. Select the `springboot-backend` directory (where `pom.xml` is located).
3. Select **Open as Maven Project**.
4. Allow Maven to download all dependencies automatically.
5. Locate `src/main/java/com/savorystay/SavoryStayApplication.java` -> Right-click -> **Run 'SavoryStayApplication'**.

### **Eclipse / STS**
1. Open Eclipse -> **File** -> **Import...**
2. Choose **Maven** -> **Existing Maven Projects** -> Click **Next**.
3. Browse to the `springboot-backend` directory and click **Finish**.

---

## 💻 Running the Application via Command Line

### 1. Configure Environment Variables (Optional or Use Defaults)

```bash
export POSTGRES_HOST=localhost
export POSTGRES_PORT=5432
export POSTGRES_DB=savorystay_db
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=postgres

export STRIPE_SECRET_KEY=sk_test_51M...
export PAYPAL_CLIENT_ID=AeX...
```

### 2. Build the Maven Executable JAR

```bash
mvn clean package
```

### 3. Run the Application

```bash
mvn spring-boot:run
```
*or directly run the JAR:*
```bash
java -jar target/savory-stay-backend-1.0.0-SNAPSHOT.jar
```

The Spring Boot server will start on **`http://localhost:8080`**.

---

## 🧪 Testing Core REST Endpoints

### 1. User Registration (Spring Security BCrypt + JWT)
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alex_chef",
    "email": "alex@savorystay.com",
    "password": "Password123!"
  }'
```

### 2. User Authentication / Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alex_chef",
    "password": "Password123!"
  }'
```

### 3. Stripe Payment Intent Creation
```bash
curl -X POST http://localhost:8080/api/v1/payments/create-intent \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 48.00,
    "currency": "USD",
    "gateway": "STRIPE",
    "customerName": "Alex Morgan"
  }'
```

---

## 🛡️ License & Copyright
Developed for **SavoryStay Culinary Operations Platform**. Open source for customization and modular expansion.
