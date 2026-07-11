# Cart Service

Spring Boot 3 microservice providing a Redis-backed shopping cart with per-user TTL expiration. Each cart is stored as a serialised JSON string in Redis under the key `cart:{userId}`.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Environment Variables](#environment-variables)
- [Running Locally](#running-locally)
- [API Endpoints](#api-endpoints)
- [Security](#security)
- [Testing & Coverage](#testing--coverage)
- [Docker](#docker)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 LTS |
| Maven | 3.9+ |
| Redis | 7.x |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CART_SERVICE_PORT` | `8083` | HTTP port the service listens on |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `CART_TTL_DAYS` | `7` | Number of days before an idle cart expires |
| `JWT_PUBLIC_KEY` | *(empty)* | PEM-encoded RSA public key (PKCS#8/X.509) used to validate RS256 JWTs |
| `CONSUL_HOST` | `localhost` | Consul agent hostname |
| `CONSUL_PORT` | `8500` | Consul agent port |
| `LOGSTASH_HOST` | `logstash` | Logstash TCP hostname for structured log shipping |
| `LOGSTASH_PORT` | `5000` | Logstash TCP port |

> **Security note:** Never hardcode `JWT_PUBLIC_KEY` — always supply it via the environment or a secrets manager.

---

## Running Locally

1. **Start Redis** (Docker convenience):
   ```bash
   docker run -d -p 6379:6379 --name redis redis:7-alpine
   ```

2. **Export required env vars:**
   ```bash
   export JWT_PUBLIC_KEY="<base64-encoded PEM public key from user-service>"
   ```

3. **Run the service:**
   ```bash
   mvn spring-boot:run
   ```

4. The service starts on `http://localhost:8083`. Health check: `GET http://localhost:8083/actuator/health`

---

## API Endpoints

All endpoints require a valid `Authorization: Bearer <JWT>` header. The JWT subject (`sub`) must match the `{userId}` path parameter, unless the caller holds `ROLE_ADMIN`.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/cart/{userId}` | Get current cart (empty if none exists) | Owner / Admin |
| `POST` | `/cart/{userId}/items` | Add item (increments quantity if product already in cart) | Owner / Admin |
| `PUT` | `/cart/{userId}/items/{productId}` | Update item quantity (0 = remove) | Owner / Admin |
| `DELETE` | `/cart/{userId}/items/{productId}` | Remove specific item | Owner / Admin |
| `DELETE` | `/cart/{userId}` | Clear entire cart | Owner / Admin |

### Response envelope

All responses follow the platform-wide shape:
```json
{
  "status": "success | error",
  "data":   { ... } | null,
  "message": null   | "error description"
}
```

### Example — add an item

```bash
curl -X POST http://localhost:8083/cart/user-123/items \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"productId":"prod-abc","productName":"Wireless Headphones","quantity":2,"unitPrice":49.99}'
```

---

## Security

- **JWT validation only** — this service holds the RS256 _public_ key; it cannot issue tokens.
- Ownership enforcement: the JWT `sub` must equal `{userId}` in the path. `ROLE_ADMIN` bypasses this check.
- Stateless sessions; CSRF disabled.
- Redis TTL is reset on every write — carts never persist without an expiry.
- No sensitive data is emitted in logs; request IDs are tracked via MDC.

---

## Testing & Coverage

Run all unit tests:
```bash
mvn test
```

Run tests with JaCoCo coverage report (requires ≥ 80% line coverage):
```bash
mvn verify
```

Coverage report is generated at `target/site/jacoco/index.html`.

Excluded from the coverage gate: `CartServiceApplication`, `dto/**`, `config/**`, `filter/**`.

---

## Docker

Build the image:
```bash
docker build -t cart-service:latest .
```

Run the container (pass env vars):
```bash
docker run -d \
  -p 8083:8083 \
  -e REDIS_HOST=redis \
  -e JWT_PUBLIC_KEY="$JWT_PUBLIC_KEY" \
  --name cart-service \
  cart-service:latest
```

The container runs as non-root user `appuser` (UID 1001).

---

## OpenAPI Specification

Full spec: [`openapi.yaml`](./openapi.yaml)
