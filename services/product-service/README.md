# Product Catalog Service

A standalone Spring Boot 3 microservice for managing the product catalog of the e-commerce platform. Backed by MongoDB with Spring Data repositories, it provides CRUD for products and categories, full-text product search, attribute-based filtering, pagination, and stock management.

---

## Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Local Run](#local-run)
- [Environment Variables](#environment-variables)
- [API Endpoints](#api-endpoints)
- [Running Tests](#running-tests)
- [Security Notes](#security-notes)

---

## Overview

| Property              | Value                                          |
|-----------------------|------------------------------------------------|
| **Port**              | `8082` (override via `PRODUCT_SERVICE_PORT`)   |
| **Language / Runtime**| Java 21, Spring Boot 3.3.2                     |
| **Database**          | MongoDB (Spring Data MongoDB)                  |
| **Auth**              | JWT validation only (RS256 public key)         |
| **Service Discovery** | Consul (`spring-cloud-starter-consul-discovery`)|
| **Metrics**           | Micrometer + Prometheus (`/actuator/prometheus`)|
| **Logging**           | Logstash-Logback structured JSON              |

---

## Prerequisites

| Tool          | Minimum Version | Notes                                     |
|---------------|-----------------|-------------------------------------------|
| Java          | 21              | Tested with Eclipse Temurin 21            |
| Maven         | 3.9+            |                                           |
| MongoDB       | 6.0+            | Or use Docker — see below                 |
| Consul        | 1.16+           | Optional for local dev — can be disabled  |

### Quick start with Docker (MongoDB only)

```bash
docker run -d --name mongodb -p 27017:27017 \
  -e MONGO_INITDB_DATABASE=productdb \
  mongo:7
```

---

## Local Run

### 1. Clone and navigate

```bash
cd services/product-service
```

### 2. Set required environment variables

```bash
export MONGO_URI=mongodb://localhost:27017/productdb
export JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----
<paste your RSA public key PEM here>
-----END PUBLIC KEY-----"
# Optional — disable Consul for local dev:
export SPRING_CLOUD_CONSUL_ENABLED=false
export SPRING_CLOUD_CONSUL_DISCOVERY_ENABLED=false
```

### 3. Build and run

```bash
mvn spring-boot:run
```

Or build the JAR and run directly:

```bash
mvn package -DskipTests
java -jar target/product-service-1.0.0.jar
```

### 4. Verify

```bash
curl http://localhost:8082/actuator/health
curl http://localhost:8082/products
```

---

## Docker

Build and run the multi-stage Docker image:

```bash
docker build -t product-service:local .
docker run -d \
  -p 8082:8082 \
  -e MONGO_URI=mongodb://host.docker.internal:27017/productdb \
  -e JWT_PUBLIC_KEY="..." \
  -e SPRING_CLOUD_CONSUL_ENABLED=false \
  product-service:local
```

The container runs as non-root user `appuser` (UID 1001).

---

## Environment Variables

| Variable                    | Default                                   | Required | Description                                      |
|-----------------------------|-------------------------------------------|----------|--------------------------------------------------|
| `PRODUCT_SERVICE_PORT`      | `8082`                                    | No       | HTTP port the service listens on                 |
| `MONGO_URI`                 | `mongodb://localhost:27017/productdb`     | Yes      | Full MongoDB connection URI                      |
| `MONGO_DB_NAME`             | `productdb`                               | No       | MongoDB database name                            |
| `JWT_PUBLIC_KEY`            | *(empty)*                                 | Yes (prod)| RSA public key PEM for JWT validation             |
| `CONSUL_HOST`               | `localhost`                               | No       | Consul agent host                                |
| `CONSUL_PORT`               | `8500`                                    | No       | Consul agent port                                |
| `LOGSTASH_HOST`             | `logstash`                                | No       | Logstash TCP host for structured log forwarding  |
| `LOGSTASH_PORT`             | `5000`                                    | No       | Logstash TCP port                                |

> **Note:** `JWT_PUBLIC_KEY` should be the Base64-encoded body of the PEM (without headers), or the full PEM block. The filter handles both formats.

---

## API Endpoints

All responses use the envelope `{ "status": "success|error", "data": <payload>, "message": <string|null> }`.

### Products

| Method   | Path                    | Auth Required          | Status | Description                          |
|----------|-------------------------|------------------------|--------|--------------------------------------|
| `GET`    | `/products`             | No                     | 200    | Search / list products (paginated)   |
| `GET`    | `/products/{id}`        | No                     | 200    | Get product by ID                    |
| `POST`   | `/products`             | ROLE_ADMIN / SERVICE   | 201    | Create product                       |
| `PUT`    | `/products/{id}`        | ROLE_ADMIN / SERVICE   | 200    | Update product                       |
| `DELETE` | `/products/{id}`        | ROLE_ADMIN / SERVICE   | 204    | Delete product                       |
| `PUT`    | `/products/{id}/stock`  | ROLE_ADMIN / SERVICE   | 200    | Adjust stock (delta positive/negative)|

#### Search query parameters (`GET /products`)

| Parameter    | Type    | Description                                             |
|--------------|---------|---------------------------------------------------------|
| `name`       | string  | Case-insensitive substring match on product name        |
| `categoryId` | string  | Filter by category ID                                   |
| `inStockOnly`| boolean | When `true`, only return products with stock > 0        |
| `page`       | integer | Zero-based page number (default `0`)                    |
| `size`       | integer | Page size (default `20`)                                |

### Categories

| Method   | Path                | Auth Required        | Status | Description            |
|----------|---------------------|----------------------|--------|------------------------|
| `GET`    | `/categories`       | No                   | 200    | List all categories    |
| `GET`    | `/categories/{id}`  | No                   | 200    | Get category by ID     |
| `POST`   | `/categories`       | ROLE_ADMIN / SERVICE | 201    | Create category        |
| `PUT`    | `/categories/{id}`  | ROLE_ADMIN / SERVICE | 200    | Update category        |
| `DELETE` | `/categories/{id}`  | ROLE_ADMIN / SERVICE | 204    | Delete category        |

> Deleting a category that still has products referencing it returns `400 Bad Request`.

### Actuator / Observability

| Path                       | Description                   |
|----------------------------|-------------------------------|
| `/actuator/health`         | Health check (Consul + Docker)|
| `/actuator/prometheus`     | Prometheus metrics scrape     |
| `/actuator/metrics`        | Spring metrics                |
| `/actuator/info`           | Build / version info          |

---

## Running Tests

### Unit tests only

```bash
mvn test
```

### Full build with coverage gate (≥ 80% line coverage)

```bash
mvn verify
```

Coverage report is generated at `target/site/jacoco/index.html`.

### Individual test class

```bash
mvn test -Dtest=ProductServiceTest
mvn test -Dtest=CategoryControllerTest
```

> Controller tests use `@WebMvcTest` with Spring Security and the embedded Flapdoodle MongoDB. No running MongoDB or Consul instance is needed for tests.

---

## Security Notes

- **JWT validation only** — this service holds no private key. It only validates tokens issued by the User Service.
- Public key is loaded from `JWT_PUBLIC_KEY` env var at startup. If absent, JWT validation is disabled (all protected endpoints return 401).
- Public read endpoints (`GET /products/**`, `GET /categories/**`, `/actuator/**`) are unauthenticated.
- All write endpoints require `ROLE_ADMIN` or `ROLE_SERVICE` in the JWT `role` claim.
- No PII or tokens are written to logs.
- Stock quantity is server-side validated — it can never go below 0.
- MongoDB queries are driven entirely by Spring Data repository methods — no string-built queries.
