# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# Uses the official Eclipse Temurin JDK 21 image with Maven bundled.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Cache Maven dependencies as a separate layer
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -q

# Build the application (skip tests; tests run in CI)
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# Minimal JRE image; runs as non-root user (UID 1001) per IBM security policy.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# Create a non-root system user
RUN groupadd --system --gid 1001 appgroup && \
    useradd  --system --uid 1001 --gid appgroup --no-create-home appuser

WORKDIR /app

# Copy the built fat JAR from the builder stage
COPY --from=builder --chown=appuser:appgroup \
    /build/target/gateway-*.jar ./gateway.jar

# Expose HTTP and HTTPS ports
EXPOSE 8080 8443

# Switch to non-root user before running
USER 1001

# Health check — allows Docker / Compose to surface readiness
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider \
        http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/gateway.jar"]
