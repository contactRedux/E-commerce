# Notification Service

Event-driven microservice that consumes Apache Kafka topics and delivers transactional notifications via **SendGrid** (email) and **Twilio** (SMS).

There are **no inbound REST endpoints** in this service. The only HTTP surface exposed is the Spring Boot Actuator:

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Liveness / readiness probe (Consul, Docker HEALTHCHECK) |
| `GET /actuator/prometheus` | Prometheus metrics scrape |

---

## Architecture

```
Kafka Topics                  Notification Service            Channels
─────────────────────────     ─────────────────────────────   ──────────────
order.placed          ──────▶ handleOrderPlaced()          ──▶ Email (SendGrid)
payment.processed     ──────▶ handlePaymentProcessed()     ──▶ Email (SendGrid)
order.status.updated  ──────▶ handleOrderStatusUpdated()   ──▶ Email (SendGrid)
                                                           ──▶ SMS   (Twilio)
```

Each listener deserialises the raw JSON string to a typed DTO using Jackson. Failures are retried 3 times with a 1-second fixed back-off via Spring Kafka's `DefaultErrorHandler`, then logged and skipped (dead-letter pattern without a dedicated DLT in this implementation).

---

## Kafka Topics Consumed

| Topic | Event DTO | Notification Sent |
|---|---|---|
| `order.placed` | `OrderPlacedEvent` | Order confirmation email |
| `payment.processed` | `PaymentProcessedEvent` | Payment receipt (SUCCEEDED) or failure (FAILED) email |
| `order.status.updated` | `OrderStatusUpdatedEvent` | Shipping update email + SMS |

---

## SendGrid Setup

1. Create a free [SendGrid](https://sendgrid.com/) account.
2. Generate an **API Key** with *Mail Send* permission.
3. Verify a sender identity (single sender or domain authentication).
4. Set the environment variable:
   ```
   SENDGRID_API_KEY=SG.your_real_key_here
   NOTIFICATION_FROM_EMAIL=noreply@yourdomain.com
   ```

> **Sandbox / CI mode**: set `SENDGRID_API_KEY=SG.placeholder` — the service will attempt the API call and log the failure at ERROR level, but will not crash the consumer. Wrap `EmailService` calls in try/catch at the consumer level for full isolation.

---

## Twilio Setup

1. Create a free [Twilio](https://www.twilio.com/) account.
2. Obtain your **Account SID** and **Auth Token** from the Twilio console.
3. Provision a phone number (or use the test magic number `+15005550006`).
4. Set the environment variables:
   ```
   TWILIO_ACCOUNT_SID=ACyour_real_sid
   TWILIO_AUTH_TOKEN=your_real_token
   TWILIO_FROM_NUMBER=+15005550001
   ```

> **Sandbox mode**: the default `TWILIO_FROM_NUMBER=+15005550006` is Twilio's magic test number that simulates a successful send without routing a real SMS.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `NOTIFICATION_SERVICE_PORT` | `8086` | HTTP port for Actuator endpoints |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address(es) |
| `CONSUL_HOST` | `localhost` | Consul agent host |
| `CONSUL_PORT` | `8500` | Consul agent port |
| `SENDGRID_API_KEY` | `SG.placeholder` | SendGrid API key — **never commit** |
| `NOTIFICATION_FROM_EMAIL` | `noreply@ecommerce.local` | Sender email address |
| `TWILIO_ACCOUNT_SID` | `ACplaceholder` | Twilio Account SID — **never commit** |
| `TWILIO_AUTH_TOKEN` | `placeholder` | Twilio Auth Token — **never commit** |
| `TWILIO_FROM_NUMBER` | `+15005550006` | Twilio sender number |

---

## Running Locally

### With Docker Compose (recommended)

```bash
# From the repository root
docker compose up notification-service
```

### Standalone

```bash
cd services/notification-service
export SENDGRID_API_KEY=SG.placeholder
export TWILIO_ACCOUNT_SID=ACplaceholder
export TWILIO_AUTH_TOKEN=placeholder
mvn spring-boot:run
```

---

## Tests & Coverage

```bash
# Run unit tests
mvn test

# Run tests + generate JaCoCo HTML report
mvn verify

# Coverage report location
open target/site/jacoco/index.html
```

Minimum line coverage threshold: **70%** (enforced by JaCoCo Maven plugin at `mvn verify`).

Excluded from coverage: `dto/**`, `exception/**`, `filter/**`, `config/**`, `NotificationServiceApplication`.

---

## Security Notes

- SendGrid API key, Twilio Auth Token, and phone numbers are **never logged** — only status codes and message SIDs appear in logs.
- All secrets are injected via environment variables. No secrets are hardcoded.
- The Docker container runs as **non-root UID 1001**.
- The service has no inbound API surface beyond Actuator health and metrics, minimising the attack surface.
