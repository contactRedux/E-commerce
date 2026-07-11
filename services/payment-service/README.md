# Payment Service

Standalone Spring Boot 3 microservice providing payment processing via **Stripe** (primary) and **PayPal** (secondary) gateways. Part of the Scalable E-Commerce Platform.

---

## Purpose

- Create Stripe PaymentIntents and return `client_secret` for front-end confirmation
- Create and capture PayPal Orders via the PayPal REST API v2 (OAuth 2.0 client credentials)
- Receive and verify Stripe webhook events (HMAC-SHA256 signature)
- Persist all payment records to PostgreSQL with idempotency key deduplication
- Publish `PaymentProcessedEvent` to Kafka topic `payment.processed` on success/failure/refund
- Exposes Prometheus metrics at `/actuator/prometheus`
- Registers with Consul for service discovery

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21 LTS |
| Maven | 3.9+ |
| PostgreSQL | 15+ |
| Kafka | 3.7+ |
| Consul | 1.18+ |

---

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PAYMENT_SERVICE_PORT` | HTTP port | `8085` |
| `PAYMENT_DB_HOST` | PostgreSQL host | `localhost` |
| `PAYMENT_DB_PORT` | PostgreSQL port | `5432` |
| `PAYMENT_DB_NAME` | Database name | `paymentdb` |
| `PAYMENT_DB_USER` | DB username | `paymentservice` |
| `PAYMENT_DB_PASSWORD` | DB password | *(required)* |
| `STRIPE_API_KEY` | Stripe secret key (`sk_test_...` or `sk_live_...`) | *(required)* |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret (`whsec_...`) | *(required)* |
| `PAYPAL_CLIENT_ID` | PayPal app client ID | *(required)* |
| `PAYPAL_CLIENT_SECRET` | PayPal app client secret | *(required)* |
| `PAYPAL_BASE_URL` | PayPal API base URL | `https://api-m.sandbox.paypal.com` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker(s) | `localhost:9092` |
| `CONSUL_HOST` | Consul host | `localhost` |
| `CONSUL_PORT` | Consul port | `8500` |
| `JWT_PUBLIC_KEY` | RSA public key PEM (issued by User Service) | *(required)* |

> **Security:** Never hardcode secrets. All sensitive values must be supplied via environment variables or a vault.

---

## Stripe Setup (Test Mode)

1. Create a free account at [stripe.com](https://stripe.com)
2. Go to **Developers → API Keys** and copy your **Secret key** (`sk_test_...`)
3. Set `STRIPE_API_KEY=sk_test_...`
4. For webhooks:
   - Install the [Stripe CLI](https://stripe.com/docs/stripe-cli)
   - Run `stripe listen --forward-to localhost:8085/payments/webhook/stripe`
   - Copy the displayed signing secret (`whsec_...`) and set `STRIPE_WEBHOOK_SECRET`
5. Stripe test card: `4242 4242 4242 4242`, any future expiry, any CVC

---

## PayPal Sandbox Setup

1. Log in to [developer.paypal.com](https://developer.paypal.com)
2. Go to **Apps & Credentials → Create App**
3. Copy the **Client ID** and **Client Secret**
4. Set `PAYPAL_CLIENT_ID` and `PAYPAL_CLIENT_SECRET`
5. The `PAYPAL_BASE_URL` defaults to the sandbox (`https://api-m.sandbox.paypal.com`)
6. PayPal Sandbox test buyer account credentials are in **Sandbox → Accounts**

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/payments/stripe/intent` | JWT | Create Stripe PaymentIntent |
| `POST` | `/payments/paypal/order` | JWT | Create PayPal Order |
| `POST` | `/payments/paypal/capture/{paypalOrderId}` | JWT | Capture approved PayPal Order |
| `POST` | `/payments/webhook/stripe` | Stripe Signature | Receive Stripe webhook events |
| `POST` | `/payments/{id}/refund` | JWT | Refund a succeeded Stripe payment |
| `GET` | `/payments/order/{orderId}` | JWT | Get payment status by order ID |

Full OpenAPI specification: [`openapi.yaml`](openapi.yaml)

### Stripe Payment Flow

```
Client                    Payment Service            Stripe
  |                            |                       |
  |-- POST /stripe/intent ---→|                       |
  |                            |-- PaymentIntent.create→|
  |                            |←-- {id, client_secret}-|
  |←-- {gatewayPaymentId,      |                       |
  |      clientSecret} --------|                       |
  |                            |                       |
  |-- confirmCardPayment() ----------------------------------→|
  |                                                    Webhook|
  |                            |←-- payment_intent.succeeded--|
  |                            |-- update DB, publish Kafka   |
```

### PayPal Payment Flow

```
Client                    Payment Service            PayPal
  |                            |                       |
  |-- POST /paypal/order ----→|                       |
  |                            |-- OAuth token request→|
  |                            |-- Create Order ------→|
  |←-- {paypalOrderId} --------|                       |
  |                            |                       |
  |-- Redirect buyer to PayPal approval URL ----------→|
  |←-- PayPal redirects buyer back with approval ------+
  |                            |                       |
  |-- POST /paypal/capture/{id}→|                      |
  |                            |-- Capture Order -----→|
  |←-- 200 OK -----------------|                       |
```

---

## Kafka Events

| Topic | Event | Description |
|-------|-------|-------------|
| `payment.processed` | `PaymentProcessedEvent` | Published when a payment succeeds, fails, or is refunded |

### `PaymentProcessedEvent` Schema

```json
{
  "paymentId": "uuid",
  "orderId": "string",
  "userId": "string",
  "amount": 49.99,
  "currency": "USD",
  "status": "SUCCEEDED | FAILED | REFUNDED",
  "gateway": "STRIPE | PAYPAL"
}
```

---

## Running Locally

```bash
# Start dependencies (from project root)
docker compose up -d postgres-payment kafka consul

# Set environment variables
export STRIPE_API_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...
export PAYPAL_CLIENT_ID=...
export PAYPAL_CLIENT_SECRET=...
export JWT_PUBLIC_KEY="$(cat path/to/public_key.pem)"

# Run the service
cd services/payment-service
mvn spring-boot:run
```

---

## Running Tests

```bash
cd services/payment-service

# Run all tests
mvn test

# Run tests with coverage report
mvn verify

# View coverage report
open target/site/jacoco/index.html
```

---

## Docker Build

```bash
cd services/payment-service
docker build -t payment-service:latest .
docker run -p 8085:8085 \
  -e STRIPE_API_KEY=sk_test_... \
  -e STRIPE_WEBHOOK_SECRET=whsec_... \
  -e PAYPAL_CLIENT_ID=... \
  -e PAYPAL_CLIENT_SECRET=... \
  -e JWT_PUBLIC_KEY="$(cat public_key.pem)" \
  payment-service:latest
```

---

## Security Notes

- Stripe API key and webhook secret are **never logged or hardcoded** — sourced from env vars only
- Webhook endpoint (`POST /payments/webhook/stripe`) is excluded from JWT auth; Stripe HMAC-SHA256 signature is verified instead
- PayPal client secret is **never logged** — sourced from env vars only
- `gatewayClientSecret` (Stripe `client_secret`) is **write-only**: returned only in the creation response, never in subsequent reads
- All monetary amounts are validated: minimum `0.01`
- Structured JSON logging — PCI data (card numbers, CVCs) is **never logged**
- Docker container runs as non-root user UID `1001`
