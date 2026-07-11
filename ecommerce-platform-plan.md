# Scalable E-Commerce Platform — Implementation Plan

## Confirmed Decisions
- **Container Registry:** GitHub Container Registry (`ghcr.io`) — more impressive to employers, integrates natively with GitHub Actions OIDC (no long-lived secrets needed)
- **Payment Gateway:** Stripe (primary) + PayPal (secondary) both implemented in Sub-Task 6
- **Service-to-service calls:** Via API Gateway using an internal service JWT (demonstrates real-world inter-service auth pattern)
- **Java version:** Java 21 LTS

## Top-Level Overview

Build a fully production-ready, containerised e-commerce platform using a **Java Spring Boot microservices architecture**. Every service is an independent Spring Boot application with its own database, Dockerfile, and OpenAPI specification. All services are wired together through a single root-level `docker-compose.yml`.

### Technology Decisions
| Concern | Choice |
|---|---|
| Language / Framework | Java 21 + Spring Boot 3.x |
| API Gateway | Spring Cloud Gateway |
| Auth | JWT (Spring Security) |
| Relational DB | PostgreSQL (User, Order, Payment) |
| Document DB | MongoDB (Product Catalog) |
| Cache / Cart | Redis |
| Messaging | Apache Kafka |
| Service Discovery | Consul |
| Logging | ELK Stack (Elasticsearch + Logstash + Kibana) |
| Metrics | Prometheus + Grafana |
| CI/CD | GitHub Actions |
| Containerisation | Docker (multi-stage builds) |

### Guiding Constraints
- No secrets hardcoded anywhere — `.env` files only
- All services bind to `127.0.0.1` or internal Docker network names; never `0.0.0.0` in production config
- All API responses: `{ "status": "success|error", "data": {}, "message": "" }`
- Structured JSON logging (request ID, service name, method, path, status, duration)
- Parameterised queries / JPA repositories only (no string-concatenated SQL)
- Each service must start independently with graceful retry/degradation logic
- HTTPS via self-signed certificates for local Docker Compose; TLS 1.2+ enforced

---

## Project Directory Layout

```
ecommerce-platform/
├── gateway/                          # Spring Cloud Gateway
├── services/
│   ├── user-service/
│   ├── product-service/
│   ├── cart-service/
│   ├── order-service/
│   ├── payment-service/
│   └── notification-service/
├── infrastructure/
│   ├── consul/
│   ├── elk/
│   ├── prometheus/
│   └── grafana/
├── .github/workflows/
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Sub-Tasks

---

### Sub-Task 1 — Project Scaffold & Root Infrastructure Files
**Status:** [ ] pending

**Intent**
Create the repository skeleton, root `docker-compose.yml`, `.env.example`, and all infrastructure configuration files (Consul, ELK, Prometheus, Grafana, SSL certs) so that every subsequent sub-task can slot a service in without re-touching root files.

**Expected Outcomes**
- `docker-compose.yml` at root defines all containers, networks, volumes, health checks, and `.env` references
- `.env.example` documents every required environment variable with placeholder values
- `infrastructure/consul/consul.hcl` — Consul server config
- `infrastructure/elk/logstash.conf`, `elasticsearch.yml`, `kibana.yml` — full ELK config
- `infrastructure/prometheus/prometheus.yml` — scrape targets for all six services + gateway
- `infrastructure/grafana/dashboards/ecommerce.json` — pre-built Grafana dashboard JSON
- `infrastructure/grafana/provisioning/` — datasource and dashboard provisioning YAML
- Self-signed TLS cert generation script at `infrastructure/certs/generate-certs.sh`
- Root `README.md` fully populated (architecture diagram, setup steps, env reference, API reference, deployment notes)

**Todo List**
1. Create `docker-compose.yml` with services: `consul`, `zookeeper`, `kafka`, `postgres-user`, `postgres-order`, `postgres-payment`, `mongodb`, `redis`, `elasticsearch`, `logstash`, `kibana`, `prometheus`, `grafana`, `gateway`, and all six microservices. Include named volumes, a `ecommerce-net` bridge network, health checks, and `--scale`-compatible service definitions.
2. Create `.env.example` with entries for: JWT secret, DB passwords (user/order/payment), MongoDB URI, Redis URL, Kafka brokers, Stripe API key, SendGrid API key, Twilio credentials, Consul address, service ports.
3. Write `infrastructure/consul/consul.hcl` — single-node dev server config with UI enabled, health check interval of 10 s.
4. Write `infrastructure/elk/logstash.conf` — beats input on 5044, JSON filter, Elasticsearch output.
5. Write `infrastructure/elk/elasticsearch.yml` — single-node, `xpack.security.enabled: false` for dev.
6. Write `infrastructure/elk/kibana.yml` — connect to Elasticsearch, expose on port 5601.
7. Write `infrastructure/prometheus/prometheus.yml` — global scrape interval 15 s, scrape targets for all service actuator `/actuator/prometheus` endpoints.
8. Write `infrastructure/grafana/provisioning/datasources/prometheus.yml` and `infrastructure/grafana/provisioning/dashboards/ecommerce.yml`.
9. Write `infrastructure/grafana/dashboards/ecommerce.json` — panels for request rate, error rate, and p99 latency per service.
10. Write `infrastructure/certs/generate-certs.sh` — `openssl req` to generate a self-signed cert for local HTTPS.
11. Write root `README.md` with Mermaid architecture diagram, prerequisites, `docker compose up` instructions, test instructions, service UI URLs, env variable table, API endpoint reference per service, and Docker Swarm / Kubernetes deployment notes.

**Relevant Context**
- Spring Boot Actuator exposes `/actuator/prometheus` when `micrometer-registry-prometheus` is on the classpath
- Consul service registration will be done via `spring-cloud-consul-discovery` in each service (Sub-Tasks 2–7)
- Kafka topics to create in `docker-compose.yml` init: `order.placed`, `payment.processed`, `order.status.updated`

---

### Sub-Task 2 — User Service
**Status:** [ ] pending

**Intent**
Implement a standalone Spring Boot service for user registration, login, JWT issuance, profile management, and RBAC. This service is the auth authority; the gateway will validate tokens it issues.

**Expected Outcomes**
- `services/user-service/` is a complete, independently runnable Spring Boot 3 project
- Endpoints: `POST /auth/register`, `POST /auth/login`, `GET /users/{id}`, `PUT /users/{id}`, `DELETE /users/{id}`, `GET /users` (admin only)
- JWT tokens issued with RS256 (private key in env var, public key shared to gateway)
- Passwords hashed with BCrypt (strength 12)
- Roles: `ROLE_CUSTOMER`, `ROLE_ADMIN`
- PostgreSQL schema managed by Flyway migrations
- OpenAPI spec at `services/user-service/openapi.yaml`
- Dockerfile using multi-stage build
- Unit tests ≥ 80% coverage on service layer
- `services/user-service/README.md`

**Todo List**
1. Initialise Maven project with dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `jjwt-api`/`jjwt-impl`/`jjwt-jackson` (latest stable), `postgresql` driver, `flyway-core`, `logstash-logback-encoder`, `spring-cloud-starter-consul-discovery`.
2. Define `User` JPA entity: `id` (UUID), `email` (unique), `passwordHash`, `firstName`, `lastName`, `role` (enum), `createdAt`, `updatedAt`.
3. Write Flyway migration `V1__create_users_table.sql`.
4. Implement `UserRepository extends JpaRepository`.
5. Implement `AuthService` with `register()` and `login()` methods; use `BCryptPasswordEncoder`.
6. Implement `JwtService` that signs/validates JWTs using RS256; private key from env var `JWT_PRIVATE_KEY`.
7. Implement `UserService` for profile CRUD with ownership checks (users may only edit their own profile; admins may edit any).
8. Implement `AuthController` and `UserController`; apply `@Valid` on all request bodies.
9. Add global exception handler (`@RestControllerAdvice`) returning `{ "status": "error", "data": null, "message": "..." }`.
10. Add structured JSON request/response logging filter using `logstash-logback-encoder`.
11. Configure Spring Security: stateless session, JWT filter, role-based endpoint protection.
12. Configure Consul registration in `application.yml` with health check pointing to `/actuator/health`.
13. Write `openapi.yaml` documenting all endpoints with request/response schemas.
14. Write multi-stage `Dockerfile`: stage 1 = `maven:3.9-eclipse-temurin-21` build; stage 2 = `eclipse-temurin:21-jre-jammy` runtime as non-root user `appuser`.
15. Write unit tests for `AuthService` and `UserService` (mock repository); target ≥ 80% line coverage.
16. Write `services/user-service/README.md`.

**Relevant Context**
- JWT public key must also be exposed (e.g. at `/auth/public-key`) so the gateway can fetch it on startup
- BCrypt strength 12 is the current IBM-recommended minimum work factor
- All DB interactions go through JPA repository methods — no native SQL except in Flyway migrations

---

### Sub-Task 3 — Product Catalog Service
**Status:** [ ] pending

**Intent**
Implement a standalone Spring Boot service backed by MongoDB for flexible product schemas. Supports CRUD for products and categories, inventory tracking, search, filtering, and pagination.

**Expected Outcomes**
- `services/product-service/` is a complete, independently runnable Spring Boot 3 project
- Endpoints: `POST /products`, `GET /products` (search + filter + paginate), `GET /products/{id}`, `PUT /products/{id}`, `DELETE /products/{id}`, `POST /categories`, `GET /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}`, `PUT /products/{id}/stock`
- MongoDB documents managed by Spring Data MongoDB
- OpenAPI spec at `services/product-service/openapi.yaml`
- Dockerfile using multi-stage build
- Unit tests ≥ 80% coverage on service layer
- `services/product-service/README.md`

**Todo List**
1. Initialise Maven project with dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `spring-boot-starter-security` (JWT validation only — public key from env), `logstash-logback-encoder`, `spring-cloud-starter-consul-discovery`.
2. Define `Product` MongoDB document: `id`, `name`, `description`, `price`, `categoryId`, `stockQuantity`, `imageUrl`, `attributes` (Map), `createdAt`, `updatedAt`.
3. Define `Category` MongoDB document: `id`, `name`, `parentCategoryId`, `description`.
4. Implement `ProductRepository extends MongoRepository` with custom `@Query` methods for search/filter.
5. Implement `CategoryRepository extends MongoRepository`.
6. Implement `ProductService` with create, read, update, delete, stock adjustment, and paginated search.
7. Implement `ProductController` and `CategoryController`; apply `@Valid` on all request bodies.
8. Add global exception handler.
9. Add structured JSON request/response logging filter.
10. Configure JWT validation filter (read-only public key from env var `JWT_PUBLIC_KEY`; validate on write endpoints).
11. Configure Consul registration.
12. Write `openapi.yaml`.
13. Write multi-stage `Dockerfile`.
14. Write unit tests for `ProductService`.
15. Write `services/product-service/README.md`.

**Relevant Context**
- Stock management is an eventual-consistency concern; the Order Service will call `PUT /products/{id}/stock` to decrement stock after an order is confirmed
- Pagination: use Spring Data `Pageable`; return `{ "status": "success", "data": { "items": [], "page": 0, "size": 20, "total": 0 } }`

---

### Sub-Task 4 — Shopping Cart Service
**Status:** [ ] pending

**Intent**
Implement a standalone Spring Boot service using Redis as the backing store for per-user cart state with TTL-based expiration.

**Expected Outcomes**
- `services/cart-service/` is a complete, independently runnable Spring Boot 3 project
- Endpoints: `GET /cart/{userId}`, `POST /cart/{userId}/items`, `PUT /cart/{userId}/items/{productId}`, `DELETE /cart/{userId}/items/{productId}`, `DELETE /cart/{userId}`
- Cart stored as a Redis Hash keyed by `cart:{userId}`; TTL = 7 days (configurable via env)
- JWT validation on all endpoints
- OpenAPI spec, Dockerfile, unit tests ≥ 80%, README

**Todo List**
1. Initialise Maven project with dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-redis`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `spring-boot-starter-security`, `logstash-logback-encoder`, `spring-cloud-starter-consul-discovery`.
2. Define `CartItem` POJO: `productId`, `quantity`, `price`, `productName`.
3. Define `Cart` POJO: `userId`, `items` (List<CartItem>), `updatedAt`.
4. Implement `CartRepository` using `RedisTemplate<String, Cart>`; serialize as JSON using Jackson.
5. Implement `CartService` with get, add item, update item quantity, remove item, clear cart. Each write operation resets TTL.
6. Implement `CartController`.
7. Add global exception handler, structured logging filter, JWT validation filter.
8. Configure Consul registration.
9. Write `openapi.yaml`.
10. Write multi-stage `Dockerfile`.
11. Write unit tests for `CartService` (mock `RedisTemplate`).
12. Write `services/cart-service/README.md`.

**Relevant Context**
- Cart TTL env var: `CART_TTL_DAYS` (default 7)
- Redis connection details from env vars `REDIS_HOST`, `REDIS_PORT`

---

### Sub-Task 5 — Order Service
**Status:** [ ] pending

**Intent**
Implement a standalone Spring Boot service that converts cart contents into persistent orders, manages the order status lifecycle, and publishes Kafka events when order state changes.

**Expected Outcomes**
- `services/order-service/` is a complete, independently runnable Spring Boot 3 project
- Endpoints: `POST /orders` (place order from cart), `GET /orders/{id}`, `GET /orders/user/{userId}`, `PUT /orders/{id}/status` (admin), `DELETE /orders/{id}` (cancel, idempotent)
- Order status lifecycle: `PENDING → CONFIRMED → SHIPPED → DELIVERED` or `CANCELLED`
- Publishes `order.placed` Kafka event after successful placement; `order.status.updated` on every status change
- PostgreSQL with Flyway migrations
- OpenAPI spec, Dockerfile, unit tests ≥ 80%, README

**Todo List**
1. Initialise Maven project with dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `spring-boot-starter-security`, `spring-kafka`, `logstash-logback-encoder`, `spring-cloud-starter-consul-discovery`, `postgresql` driver, `flyway-core`.
2. Define `Order` JPA entity: `id` (UUID), `userId`, `status` (enum), `totalAmount`, `createdAt`, `updatedAt`.
3. Define `OrderItem` JPA entity: `id`, `orderId`, `productId`, `quantity`, `unitPrice`.
4. Write Flyway migrations `V1__create_orders_table.sql` and `V2__create_order_items_table.sql`.
5. Implement `OrderRepository` and `OrderItemRepository`.
6. Implement `KafkaProducerService` that publishes serialised order event POJOs to `order.placed` and `order.status.updated` topics.
7. Implement `OrderService`: place order (calls Cart Service REST to fetch cart, calls Product Service REST to validate stock, persists order, publishes event), get order, list by user, update status, cancel order.
8. Implement `OrderController`.
9. Add global exception handler, structured logging filter, JWT validation filter.
10. Configure Kafka producer in `application.yml` using env vars `KAFKA_BOOTSTRAP_SERVERS`.
11. Configure Consul registration.
12. Write `openapi.yaml`.
13. Write multi-stage `Dockerfile`.
14. Write unit tests for `OrderService` (mock repository and Kafka producer).
15. Write `services/order-service/README.md`.

**Relevant Context**
- Use `@Transactional` on `placeOrder()` so DB write and Kafka publish either both succeed or roll back
- Idempotency: check if an order with the same `idempotencyKey` header already exists before inserting
- Order cancellation is only allowed from `PENDING` or `CONFIRMED` states

---

### Sub-Task 6 — Payment Service
**Status:** [ ] pending

**Intent**
Implement a standalone Spring Boot service that integrates with Stripe for payment intent creation, webhook handling, and refund processing. Publishes `payment.processed` Kafka events.

**Expected Outcomes**
- `services/payment-service/` is a complete, independently runnable Spring Boot 3 project
- Endpoints: `POST /payments/intent` (create Stripe PaymentIntent), `POST /payments/webhook` (Stripe webhook — no JWT, uses Stripe signature), `POST /payments/{id}/refund`, `GET /payments/{orderId}`
- Payment records persisted to PostgreSQL
- Publishes `payment.processed` Kafka event on successful charge
- Stripe API key from env var `STRIPE_API_KEY`; webhook secret from `STRIPE_WEBHOOK_SECRET`
- OpenAPI spec, Dockerfile, unit tests ≥ 80%, README

**Todo List**
1. Initialise Maven project with dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `spring-boot-starter-security`, `stripe-java` (latest stable), `spring-kafka`, `logstash-logback-encoder`, `spring-cloud-starter-consul-discovery`, `postgresql` driver, `flyway-core`.
2. Define `Payment` JPA entity: `id` (UUID), `orderId`, `stripePaymentIntentId`, `amount`, `currency`, `status` (enum: `PENDING`, `SUCCEEDED`, `FAILED`, `REFUNDED`), `createdAt`.
3. Write Flyway migration `V1__create_payments_table.sql`.
4. Implement `PaymentRepository`.
5. Implement `StripeService`: create PaymentIntent (use `Stripe.apiKey` from env, never hardcoded), process webhook event (verify signature using `WebhookSignature.constructEvent`), issue refund.
6. Implement `KafkaProducerService` publishing to `payment.processed`.
7. Implement `PaymentController`; the `/webhook` endpoint must skip JWT filter and verify Stripe signature instead.
8. Add global exception handler, structured logging filter, JWT validation filter (skip webhook route).
9. Configure Kafka producer.
10. Configure Consul registration.
11. Write `openapi.yaml`.
12. Write multi-stage `Dockerfile`.
13. Write unit tests for `StripeService` (mock Stripe SDK).
14. Write `services/payment-service/README.md`.

**Relevant Context**
- Stripe SDK: `com.stripe:stripe-java` — check Maven Central for the latest stable version before writing the POM
- Webhook endpoint must be excluded from CSRF protection and JWT filter
- Idempotency: Stripe PaymentIntents are idempotent by `idempotencyKey` header passed through to Stripe API

---

### Sub-Task 7 — Notification Service
**Status:** [ ] pending

**Intent**
Implement a Kafka consumer service that listens to `order.placed`, `payment.processed`, and `order.status.updated` topics, then dispatches email (SendGrid) and SMS (Twilio) notifications.

**Expected Outcomes**
- `services/notification-service/` is a complete, independently runnable Spring Boot 3 project
- Consumes Kafka topics: `order.placed`, `payment.processed`, `order.status.updated`
- Sends transactional email via SendGrid on each event
- Sends SMS via Twilio on `order.status.updated` events
- No inbound REST endpoints (consumer-only service); exposes `/actuator/health` and `/actuator/prometheus`
- Credentials from env vars: `SENDGRID_API_KEY`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`
- Dockerfile, unit tests ≥ 80%, README

**Todo List**
1. Initialise Maven project with dependencies: `spring-boot-starter-web` (for actuator), `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `spring-kafka`, `sendgrid-java` (latest stable), `twilio` SDK (latest stable), `logstash-logback-encoder`, `spring-cloud-starter-consul-discovery`.
2. Define event POJOs: `OrderPlacedEvent`, `PaymentProcessedEvent`, `OrderStatusUpdatedEvent` (mirror Order/Payment Service Kafka message shapes).
3. Implement `EmailService` using SendGrid Java SDK; API key from env var `SENDGRID_API_KEY`.
4. Implement `SmsService` using Twilio Java SDK; credentials from env vars.
5. Implement `NotificationKafkaConsumer` with `@KafkaListener` methods for each topic; call `EmailService` / `SmsService` based on event type.
6. Add structured JSON logging.
7. Configure Kafka consumer group `notification-group` in `application.yml`.
8. Configure Consul registration.
9. Write multi-stage `Dockerfile`.
10. Write unit tests for consumer handler methods (mock `EmailService` and `SmsService`).
11. Write `services/notification-service/README.md`.

**Relevant Context**
- SendGrid SDK: `com.sendgrid:sendgrid-java` — verify latest stable version on Maven Central
- Twilio SDK: `com.twilio.sdk:twilio` — verify latest stable version on Maven Central
- Consumer must handle deserialization errors gracefully (dead-letter topic or log-and-skip)

---

### Sub-Task 8 — API Gateway (Spring Cloud Gateway)
**Status:** [ ] pending

**Intent**
Implement the single entry point for all client traffic using Spring Cloud Gateway. It validates JWTs, enforces rate limiting, applies CORS, routes to downstream services, and serves the OpenAPI aggregate docs at `/docs`.

**Expected Outcomes**
- `gateway/` is a complete, independently runnable Spring Boot 3 + Spring Cloud Gateway project
- Routes defined for all six services in `application.yml`
- JWT validation filter: fetches public key from User Service at startup, rejects requests with invalid/missing tokens (except `/auth/register`, `/auth/login`, `/payments/webhook`)
- Rate limiting via Spring Cloud Gateway Redis `RequestRateLimiter` filter (uses same Redis instance)
- CORS policy: configurable allowed origins via env var `ALLOWED_ORIGINS`
- HTTPS termination using self-signed cert for local dev (keystore from env var `SSL_KEYSTORE_PATH`)
- Serves static HTML at `/docs` linking to each service's OpenAPI YAML
- Registers with Consul for health visibility
- Dockerfile using multi-stage build, README

**Todo List**
1. Initialise Maven project with dependencies: `spring-cloud-starter-gateway`, `spring-boot-starter-actuator`, `spring-cloud-starter-consul-discovery`, `spring-boot-starter-data-redis-reactive`, `micrometer-registry-prometheus`, `jjwt-api`/`jjwt-impl`/`jjwt-jackson`, `logstash-logback-encoder`.
2. Define route configuration in `application.yml` for all six services with predicates, `RewritePath`, `AddRequestHeader`, and `RequestRateLimiter` filters.
3. Implement `JwtAuthenticationFilter extends AbstractGatewayFilterFactory` that validates the `Authorization: Bearer` header and rejects with 401 if invalid.
4. Define `SecurityConfig` to exclude public routes (`/auth/**`, `/payments/webhook`, `/docs/**`, `/actuator/**`) from JWT filter.
5. Configure CORS via `CorsWebFilter` bean; read allowed origins from env var.
6. Configure Redis-backed rate limiter: 100 requests/second default, configurable via env vars.
7. Place static `docs/index.html` in `src/main/resources/static/docs/` listing links to each service OpenAPI spec.
8. Configure SSL in `application.yml` using PKCS12 keystore; keystore path and password from env vars.
9. Configure Consul registration.
10. Write multi-stage `Dockerfile`.
11. Write `gateway/README.md`.

**Relevant Context**
- JWT public key is shared from User Service; gateway fetches it on startup via `WebClient` call to `GET /auth/public-key`
- Rate limiter needs Redis; the gateway and cart service share the same Redis instance

---

### Sub-Task 9 — GitHub Actions CI/CD Pipelines
**Status:** [ ] pending

**Intent**
Define one reusable GitHub Actions workflow per service (plus the gateway) with stages: lint (Checkstyle), unit test, build Docker image, push to Docker Hub, and deploy notification.

**Expected Outcomes**
- `.github/workflows/` contains one workflow file per service: `user-service.yml`, `product-service.yml`, `cart-service.yml`, `order-service.yml`, `payment-service.yml`, `notification-service.yml`, `gateway.yml`
- Each workflow triggers on push/PR to paths `services/<name>/**` or `gateway/**`
- Stages: `lint` (Checkstyle via Maven), `test` (Maven Surefire with JaCoCo coverage gate ≥ 80%), `build-and-push` (Docker buildx, push to `${{ secrets.DOCKERHUB_USERNAME }}/<service-name>:${{ github.sha }}`)
- Secrets referenced: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`; never hardcoded
- A `docker-compose.staging.yml` override file for staging environment variable substitution

**Todo List**
1. Write `.github/workflows/user-service.yml` — trigger on `push`/`pull_request` paths `services/user-service/**`; jobs: `lint`, `test`, `build-and-push`.
2. Repeat for `product-service.yml`, `cart-service.yml`, `order-service.yml`, `payment-service.yml`, `notification-service.yml`, `gateway.yml`.
3. In each workflow's `test` job, configure JaCoCo Maven plugin to fail if line coverage < 80%.
4. In each workflow's `build-and-push` job, use `docker/setup-buildx-action` and `docker/login-action`; tag images as `latest` and `${{ github.sha }}`.
5. Write `docker-compose.staging.yml` at root — override image tags to use the registry images instead of local builds, and override env vars for staging endpoints.

**Relevant Context**
- GitHub Actions `secrets.DOCKERHUB_USERNAME` and `secrets.DOCKERHUB_TOKEN` must be documented in the root README
- JaCoCo plugin: `org.jacoco:jacoco-maven-plugin` — verify latest stable version

---

## Implementation Order

The sub-tasks should be executed strictly in this order, as each builds on the previous:

```
1 → Root Infrastructure & Compose
2 → User Service           (auth authority, JWT issuer)
3 → Product Catalog Service
4 → Shopping Cart Service
5 → Order Service          (depends on Cart + Product service contracts)
6 → Payment Service
7 → Notification Service   (depends on Kafka topics defined in Order + Payment)
8 → API Gateway            (depends on all service routes and User Service JWT public key)
9 → CI/CD Pipelines        (wraps everything)
```

---

## Security Compliance Checklist

Before marking any sub-task done, verify:
- [ ] No secrets hardcoded (only `System.getenv()` or Spring `${ENV_VAR}` references)
- [ ] All containers run as non-root user (`USER 1001` in Dockerfile)
- [ ] No service binds to `0.0.0.0` — Docker internal networking handles inter-service routing
- [ ] TLS 1.2+ enforced on gateway (configured in `application.yml` `server.ssl` block)
- [ ] All DB queries via JPA/Spring Data (no string-concatenated SQL)
- [ ] Input validation with `@Valid` + Bean Validation on all controller request bodies
- [ ] No PII or secrets in structured log output
- [ ] BCrypt strength ≥ 12 for password hashing
- [ ] JWT signed with RS256 (asymmetric) — private key in User Service only, public key distributed to gateway
