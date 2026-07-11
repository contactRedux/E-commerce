# User Service

The **User Service** is the authentication authority for the Scalable E-Commerce Platform. It handles user registration, login, JWT issuance (RS256), and profile management (CRUD with ownership-based access control).

---

## Purpose

| Responsibility | Detail |
|---|---|
| Registration | Validates uniqueness, hashes password (BCrypt 12), persists user |
| Authentication | Verifies credentials, issues RS256-signed JWT |
| JWT authority | Exposes RSA public key at `GET /auth/public-key` so the API Gateway can verify tokens |
| Profile management | Users manage their own profiles; admins can manage any user |
| Observability | Prometheus metrics at `/actuator/prometheus`, structured JSON logs |

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| PostgreSQL | 15+ |
| Docker (optional) | 24+ |

---

## Running Locally

### Without Docker

1. **Start PostgreSQL** and create the database:
   ```sql
   CREATE DATABASE userdb;
   CREATE USER userservice WITH PASSWORD 'changeme_user';
   GRANT ALL PRIVILEGES ON DATABASE userdb TO userservice;
   ```

2. **Generate RSA key pair** (RS256):
   ```bash
   openssl genrsa -out private.pem 2048
   openssl rsa -in private.pem -pubout -out public.pem
   # Convert to PKCS8 for Java
   openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in private.pem -out private_pkcs8.pem
   ```

3. **Export environment variables**:
   ```bash
   export JWT_PRIVATE_KEY="$(cat private_pkcs8.pem | tr -d '\n' | sed 's/-----BEGIN PRIVATE KEY-----//' | sed 's/-----END PRIVATE KEY-----//')"
   export JWT_PUBLIC_KEY="$(cat public.pem)"
   export USER_DB_HOST=localhost
   export USER_DB_PORT=5432
   export USER_DB_NAME=userdb
   export USER_DB_USER=userservice
   export USER_DB_PASSWORD=changeme_user
   ```

4. **Run the service**:
   ```bash
   cd services/user-service
   mvn spring-boot:run
   ```

### With Docker

```bash
# From repository root
docker compose up user-service
```

The service starts on port **8081** by default.

---

## Environment Variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `JWT_PRIVATE_KEY` | — | **Yes** | PKCS8 PEM-encoded RSA private key (newlines as `\n`) |
| `JWT_PUBLIC_KEY` | — | **Yes** | PEM-encoded RSA public key |
| `JWT_EXPIRATION_MS` | `86400000` | No | Token lifetime in ms (default: 24 h) |
| `USER_SERVICE_PORT` | `8081` | No | HTTP port |
| `USER_DB_HOST` | `localhost` | No | PostgreSQL host |
| `USER_DB_PORT` | `5432` | No | PostgreSQL port |
| `USER_DB_NAME` | `userdb` | No | Database name |
| `USER_DB_USER` | `userservice` | No | Database username |
| `USER_DB_PASSWORD` | `changeme_user` | **Yes (prod)** | Database password |
| `CONSUL_HOST` | `localhost` | No | Consul agent host |
| `CONSUL_PORT` | `8500` | No | Consul agent port |
| `LOGSTASH_HOST` | `logstash` | No | Logstash TCP host |
| `LOGSTASH_PORT` | `5000` | No | Logstash TCP port |

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | None | Register a new user account |
| `POST` | `/auth/login` | None | Authenticate and receive a JWT |
| `GET` | `/auth/public-key` | None | Retrieve RSA public key (PEM) |
| `GET` | `/users/{id}` | JWT | Get user by ID (own or admin) |
| `PUT` | `/users/{id}` | JWT | Update user profile (own or admin) |
| `DELETE` | `/users/{id}` | JWT | Delete user account (own or admin) |
| `GET` | `/users` | JWT (Admin) | Paginated list of all users |
| `GET` | `/actuator/health` | None | Health check |
| `GET` | `/actuator/prometheus` | None | Prometheus metrics |

Full OpenAPI specification: [`openapi.yaml`](./openapi.yaml)

---

## Running Tests

```bash
cd services/user-service
mvn test
```

### Checking Coverage

```bash
mvn verify
```

JaCoCo enforces a minimum **80% line coverage** on the service layer. The HTML report is generated at:
```
target/site/jacoco/index.html
```

---

## Project Structure

```
src/
├── main/java/com/ecommerce/userservice/
│   ├── UserServiceApplication.java
│   ├── config/          # SecurityConfig, JacksonConfig
│   ├── controller/      # AuthController, UserController
│   ├── dto/             # Request / response records
│   ├── entity/          # User JPA entity, Role enum
│   ├── exception/       # GlobalExceptionHandler, custom exceptions
│   ├── filter/          # JwtAuthenticationFilter, RequestLoggingFilter
│   ├── repository/      # UserRepository
│   └── service/         # AuthService, JwtService, UserService
└── main/resources/
    ├── application.yml
    ├── logback-spring.xml
    └── db/migration/V1__create_users_table.sql
```

---

## Security Notes

- Passwords are hashed with **BCrypt strength 12** — never stored or logged in plaintext.
- JWT tokens are signed with **RS256** (asymmetric). The private key is loaded exclusively from the `JWT_PRIVATE_KEY` environment variable — never hardcoded.
- The `passwordHash` field is **never** included in any API response.
- All request bodies are validated with `@Valid` + Bean Validation.
- The `Authorization` header and password fields are **never** logged.
- The container runs as non-root user `appuser` (UID 1001).
