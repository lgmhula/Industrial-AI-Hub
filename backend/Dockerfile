# ========================
# Industrial AI Hub — Backend Dockerfile
# Multi-stage: build (Maven + JDK 25) → run (JRE slim)
# ========================

# Stage 1: Build
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
# Download dependencies first (layer caching)
RUN chmod +x mvnw && ./mvnw -s .mvn/settings.xml dependency:go-offline -B
COPY src ./src
RUN ./mvnw -s .mvn/settings.xml package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# logs/data 目录预建并授权 app 用户：日志启动即落盘，并为
# SimpleVectorStore 持久化提供可写目录。compose 分别挂载
# backend-logs 与 backend-data named volume。
RUN addgroup -S app && adduser -S app -G app \
 && mkdir -p /app/logs /app/data && chown -R app:app /app/logs /app/data
USER app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
