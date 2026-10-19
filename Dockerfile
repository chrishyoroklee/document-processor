# Multi-stage build: each FROM starts a fresh stage. Only the last stage ends up
# in the final image, so build tools (Node, the JDK, Maven) never ship.

# --- Stage 1: build the React page into static files ---
FROM node:22-slim AS frontend
WORKDIR /app
# Copy only the package files first. Docker caches each step, so `npm ci` reruns
# only when dependencies change, not on every source edit.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the Spring Boot jar, with the page bundled inside ---
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# Same caching trick: download dependencies in their own layer.
RUN ./mvnw -q -B dependency:go-offline
COPY backend/src src
# Spring Boot serves anything in classpath:/static at the root URL, so the
# built page and the API share one origin (no CORS needed).
COPY --from=frontend /app/dist src/main/resources/static
# Tests already ran in CI and need Docker themselves (Testcontainers), so skip them here.
RUN ./mvnw -q -B -DskipTests package

# --- Stage 3: the runtime image, just a JRE and the jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# Don't run as root inside the container.
RUN groupadd --system app && useradd --system --gid app app
USER app
COPY --from=backend /app/target/docproc-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
