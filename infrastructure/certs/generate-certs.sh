#!/usr/bin/env bash
set -euo pipefail

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Generating self-signed TLS certificate for local development..."

openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 \
  -nodes \
  -keyout "${CERTS_DIR}/server.key" \
  -out "${CERTS_DIR}/server.crt" \
  -subj "/CN=localhost/O=EcommerceDev/C=US" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

openssl pkcs12 -export \
  -in "${CERTS_DIR}/server.crt" \
  -inkey "${CERTS_DIR}/server.key" \
  -out "${CERTS_DIR}/keystore.p12" \
  -name "ecommerce-local" \
  -passout pass:changeme_keystore

echo "Certificate generated at: ${CERTS_DIR}/server.crt"
echo "PKCS12 keystore generated at: ${CERTS_DIR}/keystore.p12"
echo "Keystore password: changeme_keystore"
echo ""
echo "IMPORTANT: These are development-only self-signed certificates."
echo "Do NOT use in production."
