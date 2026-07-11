# Order Service

A Spring Boot 3.3.2 microservice that manages the order lifecycle for the Scalable E-Commerce Platform. It persists orders to PostgreSQL, enforces a strict status-transition state machine, and publishes Kafka events on every state change.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Local Run](#local-run)
- [Environment Variables](#environment-variables)
- [API Endpoints](#api-endpoints)
- [Kafka Topics](#kafka-topics)
- [Order Status Lifecycle](#order-status-lifecycle)
- [Testing & Coverage](#testing--coverage)
- [Security Notes](#security-notes)

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21 LTS |
| Maven | 3.9+ |
| PostgreSQL | 15+ |
| Apache Kafka | 3.x |
| Consul | 1.17+ (optional for local dev) |

---

## Local Run

### 1. Start dependencies (Docker Compose from repo root)

```bash
docker compose up -d postgres-order kafka consul
```

### 2. Configure environment

Copy and edit the env file:

```bash
cp ../../.env.example .env
```

### 3. Run the service

```bash
mvn spring-boot:run
```

The service starts on **port 8084** by default.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `ORDER_SERVICE_PORT` | `8084` | HTTP port |
| `ORDER_DB_HOST` | `localhost` | PostgreSQL host |
| `ORDER_DB_PORT` | `5432` | PostgreSQL port |
| `ORDER_DB_NAME` | `orderdb` | Database name |
| `ORDER_DB_USER` | `orderservice` | Database username |
| `ORDER_DB_PASSWORD` | — | Database password (**required**) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker(s) |
| `JWT_PUBLIC_KEY` | — | RS256 public key PEM (from User Service) |
| `CONSUL_HOST` | `localhost` | Consul host |
| `CONSUL_PORT` | `8500` | Consul port |
| `GATEWAY_BASE_URL` | `http://gateway:8080` | API Gateway base URL for WebClient |

---

## API Endpoints

All endpoints require a valid `Authorization: Bearer <JWT>` header unless stated otherwise.

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/orders` | Customer / Admin | Place a new order (idempotent via `idempotencyKey`) |
| `GET` | `/orders/{id}` | Owner / Admin | Retrieve a single order |
| `GET` | `/orders/user/{userId}` | Owner / Admin | List orders for a user (paginated) |
| `PUT` | `/orders/{id}/status` | **Admin only** | Advance order status |
| `DELETE` | `/orders/{id}` | Owner / Admin | Cancel an order |

### Query Parameters for `GET /orders/user/{userId}`

| Param | Default | Description |
|---|---|---|
| `page` | `0` | Zero-based page number |
| `size` | `20` | Page size (max 100) |

### Response Envelope

All responses use the standard envelope:

```json
{
  "status": "success | error",
  "data": { ... },
  "message": null
}
```

---

## Kafka Topics

| Topic | Event | Published When |
|---|---|---|
| `order.placed` | `OrderPlacedEvent` | New order successfully persisted |
| `order.status.updated` | `OrderStatusUpdatedEvent` | Order status changes (including cancellation) |

### `OrderPlacedEvent` schema

```json
{
  "orderId": "uuid",
  "userId": "string",
  "totalAmount": 99.98,
  "shippingAddress": "123 Main St",
  "items": [
    { "productId": "...", "productName": "...", "quantity": 2, "unitPrice": 49.99 }
  ]
}
```

### `OrderStatusUpdatedEvent` schema

```json
{
  "orderId": "uuid",
  "userId": "string",
  "previousStatus": "PENDING",
  "newStatus": "CONFIRMED"
}
```

---

## Order Status Lifecycle

```
PENDING ──► CONFIRMED ──► SHIPPED ──► DELIVERED
   │              │
   └──────────────┴──────────────────────► CANCELLED
```

- Only **Admin** can advance status via `PUT /orders/{id}/status`.
- Customers and Admins can cancel orders in `PENDING` or `CONFIRMED` state.
- Any other transition throws `409 Conflict`.

---

## Testing & Coverage

Run all tests with coverage report:

```bash
mvn test
```

Run with JaCoCo HTML report:

```bash
mvn verify
open target/site/jacoco/index.html
```

The JaCoCo coverage gate enforces **≥ 80% line coverage** on the service and controller layers. Entities, DTOs, enums, exceptions, filters, and config classes are excluded from the gate.

---

## Security Notes

- JWT validation uses **RS256 public key only** — no private key is present in this service.
- All database access goes through Spring Data JPA repositories (no string-concatenated SQL).
- `@Valid` annotation enforced on all controller request bodies.
- Ownership checks prevent customers from accessing or cancelling other users' orders.
- Docker container runs as non-root user UID 1001.
- No sensitive data (user IDs, addresses, amounts) written to logs at DEBUG level or above.
