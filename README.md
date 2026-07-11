# 🛒 Scalable E-Commerce Platform

A **production-ready, microservices-based e-commerce platform** built with Java 21, Spring Boot 3, and Docker. Every feature is implemented as an independent, containerised microservice with its own database, CI/CD pipeline, and API specification — demonstrating real-world distributed systems design.

---

## 🎯 Project Purpose

This project was built to demonstrate enterprise-level software engineering skills across the full stack of a modern distributed system:

- **Microservices architecture** — each service is independently deployable, scalable, and fault-tolerant
- **Event-driven design** — services communicate asynchronously via Apache Kafka for loose coupling
- **API Gateway pattern** — single entry point with JWT auth, rate limiting, CORS, and HTTPS
- **Observability** — centralised structured logging (ELK), distributed metrics (Prometheus + Grafana), and service discovery (Consul)
- **Security-first** — RS256 JWT tokens, BCrypt password hashing, no hardcoded secrets, TLS everywhere
- **CI/CD** — GitHub Actions pipelines with OIDC-based image pushing to GitHub Container Registry, JaCoCo coverage gates, and multi-platform Docker builds

This is the kind of platform that powers companies like Amazon, Shopify, and eBay at their core.

---

## 🏗 Architecture

```
                        ┌─────────────────────────────────────────────┐
                        │           API Gateway (port 8080/8443)       │
                        │        Spring Cloud Gateway — HTTPS          │
                        │  JWT validation · Rate limiting · CORS       │
                        └──────┬──────┬──────┬──────┬──────┬──────────┘
                               │      │      │      │      │
               ┌───────────────┘      │      │      │      └────────────────┐
               │                      │      │      │                        │
               ▼                      ▼      ▼      ▼                        ▼
    ┌──────────────────┐  ┌───────────────┐  ┌────────────────┐  ┌─────────────────┐
    │   User Service   │  │Product Service│  │  Cart Service  │  │  Order Service  │
    │   (port 8081)    │  │  (port 8082)  │  │  (port 8083)   │  │  (port 8084)    │
    │   PostgreSQL     │  │   MongoDB     │  │    Redis       │  │   PostgreSQL    │
    └──────────────────┘  └───────────────┘  └────────────────┘  └────────┬────────┘
                                                                           │
                                                          ┌────────────────┘
                                                          │
                                                          ▼  Kafka Events
                                              ┌─────────────────────┐
                                              │   Payment Service   │◄──── Stripe / PayPal
                                              │    (port 8085)      │
                                              │    PostgreSQL       │
                                              └──────────┬──────────┘
                                                         │
                                              ┌──────────┘
                                              │  Kafka (order.placed,
                                              │          payment.processed,
                                              │          order.status.updated)
                                              ▼
                                  ┌─────────────────────────┐
                                  │  Notification Service   │
                                  │      (port 8086)        │
                                  │  SendGrid · Twilio      │
                                  └─────────────────────────┘

  Infrastructure:
  ┌──────────┐  ┌───────────────────────┐  ┌────────────────────┐
  │  Consul  │  │  ELK Stack            │  │  Prometheus+Grafana │
  │ (8500)   │  │  ES:9200 Kibana:5601  │  │  Prom:9090 Graf:3000│
  └──────────┘  └───────────────────────┘  └────────────────────┘
```

### Inter-Service Communication
| Type | Protocol | Used for |
|---|---|---|
| Synchronous | REST over HTTP (via Gateway) | Client requests (place order, fetch product) |
| Asynchronous | Apache Kafka | Internal events (order placed → send notification) |

### Kafka Topics
| Topic | Published by | Consumed by |
|---|---|---|
| `order.placed` | Order Service | Notification Service |
| `order.status.updated` | Order Service | Notification Service |
| `payment.processed` | Payment Service | Notification Service |

---

## 📦 Services at a Glance

| Service | Port | Database | Key Responsibilities |
|---|---|---|---|
| **API Gateway** | 8080/8443 | Redis (rate limiter) | JWT auth, routing, rate limiting, CORS, HTTPS |
| **User Service** | 8081 | PostgreSQL | Registration, login, JWT issuance, RBAC |
| **Product Service** | 8082 | MongoDB | Product/category CRUD, search, stock management |
| **Cart Service** | 8083 | Redis | Per-user cart with TTL expiration |
| **Order Service** | 8084 | PostgreSQL | Order lifecycle, Kafka events |
| **Payment Service** | 8085 | PostgreSQL | Stripe + PayPal, webhooks, refunds |
| **Notification Service** | 8086 | None (stateless) | Email (SendGrid) + SMS (Twilio) via Kafka |

---

## ✅ Prerequisites

Before you start, make sure you have these installed. Click each link for installation instructions:

| Tool | Version | Purpose | Install |
|---|---|---|---|
| **Docker Desktop** | 4.x+ | Runs all containers | [docker.com/get-docker](https://www.docker.com/get-docker) |
| **Docker Compose** | v2 (included with Docker Desktop) | Orchestrates all services | Included with Docker Desktop |
| **Git** | Any | Clone this repo | [git-scm.com](https://git-scm.com/) |
| **Java 21** | 21 LTS | Build services locally (optional) | [adoptium.net](https://adoptium.net/) |
| **Maven** | 3.9+ | Build tool (optional) | [maven.apache.org](https://maven.apache.org/) |

> **Note:** Java and Maven are only needed if you want to run services or tests **without** Docker. If you only want to run the full stack with Docker, you only need Docker Desktop and Git.

---

## 🚀 Quick Start — Run the Full Platform

### Step 1 — Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/scalable-ecommerce-platform.git
cd "scalable-ecommerce-platform"
```

### Step 2 — Set up environment variables

Copy the example environment file and fill in your values:

```bash
cp .env.example .env
```

Open `.env` in any text editor. The minimum you must change for local development:

```bash
# These are the only values you MUST change to run locally:
JWT_PRIVATE_KEY=<generate below>
JWT_PUBLIC_KEY=<generate below>

# These can stay as-is for local testing (sandbox/test mode):
STRIPE_API_KEY=sk_test_...        # Get free from https://dashboard.stripe.com
SENDGRID_API_KEY=SG....           # Get free from https://sendgrid.com
TWILIO_ACCOUNT_SID=AC...          # Get free from https://twilio.com
TWILIO_AUTH_TOKEN=...
```

**Generate the RSA key pair for JWT:**

```bash
# Generate private key
openssl genrsa -out private.pem 4096

# Extract public key
openssl rsa -in private.pem -pubout -out public.pem

# Format for .env (replace actual newlines with \n)
echo "JWT_PRIVATE_KEY=$(cat private.pem | tr '\n' '|' | sed 's/|/\\n/g')"
echo "JWT_PUBLIC_KEY=$(cat public.pem | tr '\n' '|' | sed 's/|/\\n/g')"

# Clean up
rm private.pem public.pem
```

Copy the output values into your `.env` file.

### Step 3 — Generate TLS certificates (for HTTPS)

```bash
bash infrastructure/certs/generate-certs.sh
```

This creates `infrastructure/certs/keystore.p12` used by the API Gateway for HTTPS. The password is `changeme_keystore` (already set in `.env.example`).

### Step 4 — Start the full platform

```bash
docker compose up --build
```

This will:
1. Pull all infrastructure images (PostgreSQL, MongoDB, Redis, Kafka, Consul, ELK, Prometheus, Grafana)
2. Build all 7 Spring Boot services from source
3. Start everything in dependency order

**First startup takes 5–10 minutes** because Maven downloads dependencies and builds each service. Subsequent starts are much faster.

### Step 5 — Verify everything is running

```bash
docker compose ps
```

All services should show `healthy` or `running`. Then open:

| UI | URL | Purpose |
|---|---|---|
| **API Gateway** | https://localhost:8443 | Main entry point for all API calls |
| **API Docs** | https://localhost:8443/docs | Links to all service OpenAPI specs |
| **Consul UI** | http://localhost:8500 | Service registry and health dashboard |
| **Grafana** | http://localhost:3000 | Metrics dashboards (admin/admin) |
| **Kibana** | http://localhost:5601 | Centralised log search |
| **Prometheus** | http://localhost:9090 | Raw metrics explorer |

> **Browser HTTPS warning:** Your browser will show a security warning for `localhost:8443` because the certificate is self-signed. Click "Advanced → Proceed to localhost" to continue. This is expected in local development.

---

## 🧪 Testing the APIs

Once running, try these example API calls using `curl` or any REST client (e.g. [Postman](https://postman.com)):

### Register a new user
```bash
curl -k -X POST https://localhost:8443/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "SecurePass123!",
    "firstName": "Alice",
    "lastName": "Smith"
  }'
```

### Log in and get a JWT token
```bash
curl -k -X POST https://localhost:8443/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "SecurePass123!"
  }'
# Save the token from the response as TOKEN=...
```

### Browse products (public — no token needed)
```bash
curl -k https://localhost:8443/products?page=0&size=10
```

### Add item to cart (requires token)
```bash
curl -k -X POST https://localhost:8443/cart/USER_ID/items \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "PRODUCT_ID",
    "productName": "Example Product",
    "quantity": 2,
    "unitPrice": 29.99
  }'
```

### Place an order (requires token)
```bash
curl -k -X POST https://localhost:8443/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "userId": "USER_ID",
    "items": [{"productId": "PRODUCT_ID", "productName": "Example", "quantity": 1, "unitPrice": 29.99}],
    "shippingAddress": "123 Main St, New York, NY 10001",
    "idempotencyKey": "unique-key-123"
  }'
```

---

## 🧑‍💻 Running Tests

### Run all tests for a single service

```bash
cd services/user-service
mvn test
```

### Run tests with coverage report

```bash
cd services/user-service
mvn verify
# Coverage report: target/site/jacoco/index.html
```

### Run tests for all services at once

```bash
for service in services/*/; do
  echo "Testing $service..."
  (cd "$service" && mvn test -q)
done
```

### Open the coverage report

After running `mvn verify`, open the HTML report in your browser:

```bash
open services/user-service/target/site/jacoco/index.html   # macOS
xdg-open services/user-service/target/site/jacoco/index.html  # Linux
```

---

## 🐳 Docker Commands Reference

```bash
# Start all services
docker compose up --build

# Start in background (detached)
docker compose up --build -d

# Stop everything
docker compose down

# Stop and delete all data (fresh start)
docker compose down -v

# View logs for a specific service
docker compose logs -f user-service

# Scale a service horizontally (e.g., 3 instances of product-service)
docker compose up --scale product-service=3 -d

# Restart a single service after code change
docker compose up --build user-service -d

# Check health of all containers
docker compose ps
```

---

## 📁 Project Structure

```
.
├── gateway/                          # Spring Cloud Gateway (API entry point)
├── services/
│   ├── user-service/                 # Auth, users, JWT issuance
│   ├── product-service/              # Products, categories, inventory
│   ├── cart-service/                 # Shopping cart (Redis)
│   ├── order-service/                # Order lifecycle
│   ├── payment-service/              # Stripe + PayPal payments
│   └── notification-service/         # Email + SMS via Kafka events
├── infrastructure/
│   ├── consul/                       # Service discovery config
│   ├── elk/                          # Elasticsearch, Logstash, Kibana config
│   ├── prometheus/                   # Prometheus scrape config
│   ├── grafana/                      # Grafana dashboards + provisioning
│   └── certs/                        # TLS certificate generation script
├── .github/
│   └── workflows/                    # GitHub Actions CI/CD (one per service)
├── docker-compose.yml                # Full local dev stack
├── docker-compose.staging.yml        # Staging override (uses ghcr.io images)
├── .env.example                      # Environment variable template
└── README.md                         # This file
```

Each service follows the same internal structure:
```
<service>/
├── pom.xml                           # Maven build file
├── Dockerfile                        # Multi-stage Docker build
├── openapi.yaml                      # OpenAPI 3.0 API specification
├── README.md                         # Service-specific documentation
└── src/
    ├── main/java/...                 # Application source code
    └── test/java/...                 # Unit tests
```

---

## 🔑 Environment Variables Reference

Copy `.env.example` to `.env` and fill in your values. Never commit `.env` to Git.

| Variable | Required | Description |
|---|---|---|
| `JWT_PRIVATE_KEY` | ✅ Yes | RSA 4096 private key (PEM) — User Service only |
| `JWT_PUBLIC_KEY` | ✅ Yes | RSA 4096 public key (PEM) — all services + gateway |
| `JWT_EXPIRATION_MS` | No (default: 86400000) | Token expiry in milliseconds (default 24h) |
| `USER_DB_PASSWORD` | ✅ Yes | PostgreSQL password for User Service |
| `ORDER_DB_PASSWORD` | ✅ Yes | PostgreSQL password for Order Service |
| `PAYMENT_DB_PASSWORD` | ✅ Yes | PostgreSQL password for Payment Service |
| `MONGO_URI` | No (default: internal) | MongoDB connection URI |
| `REDIS_HOST` | No (default: redis) | Redis hostname |
| `KAFKA_BOOTSTRAP_SERVERS` | No (default: kafka:9092) | Kafka broker address |
| `STRIPE_API_KEY` | ✅ For payments | Stripe API key (use `sk_test_...` for testing) |
| `STRIPE_WEBHOOK_SECRET` | ✅ For webhooks | Stripe webhook signing secret |
| `PAYPAL_CLIENT_ID` | ✅ For PayPal | PayPal sandbox app client ID |
| `PAYPAL_CLIENT_SECRET` | ✅ For PayPal | PayPal sandbox app client secret |
| `SENDGRID_API_KEY` | ✅ For email | SendGrid API key |
| `TWILIO_ACCOUNT_SID` | ✅ For SMS | Twilio account SID |
| `TWILIO_AUTH_TOKEN` | ✅ For SMS | Twilio auth token |
| `TWILIO_FROM_NUMBER` | ✅ For SMS | Twilio sender phone number |
| `SSL_KEYSTORE_PASSWORD` | No (default: changeme_keystore) | PKCS12 keystore password for HTTPS |
| `ALLOWED_ORIGINS` | No (default: localhost) | Comma-separated CORS allowed origins |
| `CART_TTL_DAYS` | No (default: 7) | Cart session TTL in days |
| `GITHUB_REPOSITORY_OWNER` | For staging | GitHub username (for ghcr.io image names) |

---

## 🔌 API Endpoint Reference

All endpoints are accessed through the API Gateway at `https://localhost:8443`.

### Auth & Users (`/auth/**`, `/users/**`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Register a new user |
| POST | `/auth/login` | Public | Login, receive JWT token |
| GET | `/auth/public-key` | Public | Get RSA public key (PEM) |
| GET | `/users/{id}` | JWT | Get user profile |
| PUT | `/users/{id}` | JWT (owner/admin) | Update user profile |
| DELETE | `/users/{id}` | JWT (owner/admin) | Delete user account |
| GET | `/users` | JWT (admin only) | List all users (paginated) |

### Products & Categories (`/products/**`, `/categories/**`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/products` | Public | Search/filter products (paginated) |
| GET | `/products/{id}` | Public | Get product details |
| POST | `/products` | Admin JWT | Create a product |
| PUT | `/products/{id}` | Admin JWT | Update a product |
| DELETE | `/products/{id}` | Admin JWT | Delete a product |
| PUT | `/products/{id}/stock` | Admin JWT | Update stock quantity |
| GET | `/categories` | Public | List all categories |
| POST | `/categories` | Admin JWT | Create a category |

### Shopping Cart (`/cart/**`)
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/cart/{userId}` | JWT (owner) | Get user's cart |
| POST | `/cart/{userId}/items` | JWT (owner) | Add item to cart |
| PUT | `/cart/{userId}/items/{productId}` | JWT (owner) | Update item quantity |
| DELETE | `/cart/{userId}/items/{productId}` | JWT (owner) | Remove item from cart |
| DELETE | `/cart/{userId}` | JWT (owner) | Clear entire cart |

### Orders (`/orders/**`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/orders` | JWT | Place a new order |
| GET | `/orders/{id}` | JWT (owner/admin) | Get order details |
| GET | `/orders/user/{userId}` | JWT (owner/admin) | Get user's order history |
| PUT | `/orders/{id}/status` | Admin JWT | Update order status |
| DELETE | `/orders/{id}` | JWT (owner/admin) | Cancel an order |

### Payments (`/payments/**`)
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments/stripe/intent` | JWT | Create Stripe payment intent |
| POST | `/payments/paypal/order` | JWT | Create PayPal order |
| POST | `/payments/paypal/capture/{id}` | JWT | Capture PayPal payment |
| POST | `/payments/webhook/stripe` | None (Stripe sig) | Stripe webhook handler |
| POST | `/payments/{id}/refund` | JWT (admin) | Process refund |
| GET | `/payments/order/{orderId}` | JWT (owner/admin) | Get payment for an order |

---

## 🛣 Possible Next Steps

Ready to take this further? Here are concrete improvements to add:

### Immediate Enhancements
- **Search with Elasticsearch** — Replace MongoDB text search with a dedicated Elasticsearch index for full-text product search with faceted filtering
- **Product image uploads** — Add AWS S3 or MinIO integration for product image storage
- **Shopping cart sync** — Call Product Service to validate prices when checking out (prevent stale price exploits)
- **Order confirmation emails with templates** — Replace the placeholder HTML builder with Handlebars/Thymeleaf email templates

### Scalability Improvements
- **Database read replicas** — Add PostgreSQL replica configuration for read-heavy workloads
- **Kafka partition scaling** — Increase partition count on `order.placed` topic for higher throughput
- **CDN for static assets** — Add CloudFront or Nginx caching headers for product images
- **Database connection pooling** — Configure HikariCP pool sizes per service based on load testing results

### Production Hardening
- **Kubernetes deployment** — Convert `docker-compose.yml` to Helm charts for Kubernetes deployment
- **Distributed tracing** — Add Zipkin/Jaeger with Spring Cloud Sleuth for request tracing across services
- **Circuit breakers** — Add Resilience4j circuit breakers on all inter-service HTTP calls
- **API versioning** — Add `/v1/` prefix to all routes and implement version routing at the gateway
- **Refresh tokens** — Add JWT refresh token rotation to avoid long-lived access tokens

### Feature Additions
- **Product reviews and ratings** — New `review-service` with PostgreSQL
- **Discount/coupon service** — Rule-based discount engine
- **Wishlist service** — Redis-backed user wishlists
- **Admin dashboard** — React/Vue frontend consuming the API
- **Multi-currency** — Add currency conversion in Payment Service

### Security Hardening
- **OAuth 2.0 / OIDC** — Replace custom JWT with Keycloak or Auth0 for enterprise-grade auth
- **mTLS between services** — Add mutual TLS for all internal service-to-service communication
- **Secrets management** — Migrate from `.env` files to HashiCorp Vault or AWS Secrets Manager
- **PCI-DSS compliance** — Full audit of Payment Service against PCI data security standards

---

## 🎓 Resume Skills — Technologies Used in This Project

Add these to your resume under **Technical Skills** or **Technologies**:

### Programming Languages
- **Java 21** — primary language, records, sealed classes, virtual threads

### Frameworks & Libraries
- **Spring Boot 3** — application framework for all microservices
- **Spring Cloud Gateway** — reactive API gateway with global filters
- **Spring Security** — JWT-based stateless authentication and RBAC
- **Spring Data JPA / Hibernate** — ORM for PostgreSQL (User, Order, Payment services)
- **Spring Data MongoDB** — document store integration (Product Service)
- **Spring Data Redis** — Redis integration for cart and rate limiting
- **Spring Kafka** — Kafka producer and consumer integration
- **Spring WebFlux** — reactive programming for gateway and inter-service calls
- **Spring Cloud Consul** — service registration and discovery
- **Spring Boot Actuator** — health checks, metrics endpoints
- **Flyway** — database schema version control and migrations
- **JJWT (JSON Web Tokens)** — RS256 JWT signing and validation
- **Micrometer** — application metrics abstraction layer
- **Logstash Logback Encoder** — structured JSON logging

### Databases & Storage
- **PostgreSQL** — relational database for transactional data
- **MongoDB** — document store for flexible product schemas
- **Redis** — in-memory cache and session store

### Message Brokers
- **Apache Kafka** — distributed event streaming for async inter-service communication

### DevOps & Infrastructure
- **Docker** — containerisation with multi-stage Dockerfiles
- **Docker Compose** — local multi-container orchestration
- **GitHub Actions** — CI/CD pipelines with OIDC authentication
- **GitHub Container Registry (ghcr.io)** — container image registry
- **Consul** — service discovery and health checking

### Observability
- **Prometheus** — metrics collection and alerting
- **Grafana** — metrics visualisation and dashboards
- **Elasticsearch** — log storage and full-text search
- **Logstash** — log ingestion and transformation pipeline
- **Kibana** — log visualisation and exploration (ELK Stack)

### Third-Party Integrations
- **Stripe** — payment processing (PaymentIntent API, webhooks)
- **PayPal REST API v2** — secondary payment gateway (Orders API + OAuth 2.0)
- **SendGrid** — transactional email delivery
- **Twilio** — SMS notification delivery

### Security
- **JWT / RS256** — asymmetric token signing
- **BCrypt** — password hashing
- **TLS 1.2+** — transport layer security
- **CORS** — cross-origin resource sharing policies
- **RBAC** — role-based access control (ROLE_ADMIN, ROLE_CUSTOMER)
- **OWASP principles** — input validation, parameterised queries, no secrets in code

### Architectural Patterns & Concepts
- **Microservices Architecture**
- **API Gateway Pattern**
- **Event-Driven Architecture**
- **CQRS** (Command Query Responsibility Segregation — product reads vs writes)
- **Idempotency** — safe retry for order and payment operations
- **Circuit Breaker Pattern** (groundwork laid)
- **Service Discovery**
- **Structured Logging**
- **OpenAPI 3.0** — API contract documentation
- **12-Factor App** methodology

### Testing
- **JUnit 5** — unit test framework
- **Mockito** — mocking framework for unit tests
- **Spring Boot Test** — integration test support
- **Spring Security Test** — security-aware MVC tests
- **JaCoCo** — code coverage measurement and enforcement
- **WebMvcTest / WebFluxTest** — controller slice testing

### Build Tools
- **Maven** — dependency management and build lifecycle

---

## 📊 Project Stats

| Metric | Count |
|---|---|
| Microservices | 7 (6 business + 1 gateway) |
| Databases | 3 types (PostgreSQL, MongoDB, Redis) |
| Kafka Topics | 3 |
| API Endpoints | 35+ |
| Docker containers | 20 |
| GitHub Actions workflows | 7 |
| Test files | 25+ |
| Lines of code (approx.) | 15,000+ |

---

## 📚 Further Reading

- [Spring Cloud Gateway docs](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Apache Kafka documentation](https://kafka.apache.org/documentation/)
- [Stripe API reference](https://stripe.com/docs/api)
- [12-Factor App methodology](https://12factor.net/)
- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [Martin Fowler — Microservices](https://martinfowler.com/articles/microservices.html)

---

<p align="center">
  Built with ❤️ using Java 21 · Spring Boot 3 · Docker · Apache Kafka
</p>
