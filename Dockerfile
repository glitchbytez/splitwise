# ---- Build stage: compile and package the Spring Boot jar ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Resolve dependencies first so they are cached until the pom changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/splitwise-*.jar app.jar

# ---- Runtime stage: slim JRE, non-root user ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S splitwise && adduser -S -G splitwise splitwise
COPY --from=build --chown=splitwise:splitwise /workspace/app.jar app.jar
USER splitwise

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
