# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so they're cached across builds that only change source.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -q -DskipTests package

# --- Runtime stage -------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin wingmark
COPY --from=build /build/target/wingmark-backend-*.jar app.jar
RUN mkdir -p /data/uploads && chown -R wingmark:wingmark /app /data
USER wingmark

# Render (and most PaaS platforms) inject $PORT at runtime; application.yml
# already falls back to it ahead of SERVER_PORT.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
