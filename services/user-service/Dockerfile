# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Install Maven
RUN apt-get update && \
    apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/*

# Download dependencies first (layer cache)
COPY pom.xml .
RUN mvn -B dependency:go-offline -q

COPY src ./src
RUN mvn -B package -DskipTests -q

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-jammy

RUN useradd -m -u 1001 appuser

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
RUN chown appuser:appuser app.jar

USER 1001

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
