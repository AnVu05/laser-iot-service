# ==========================================
# Stage 1: Build Application
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and POM first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copy source code and build executable jar
COPY src/ src/
RUN ./mvnw clean package -DskipTests

# ==========================================
# Stage 2: Lightweight Runtime (Temurin JRE 21 Alpine)
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root system user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy JAR from build stage
COPY --from=builder /app/target/laser-iot-service-*.jar app.jar

# Configuration for Render Free Tier (Memory & Port)
ENV PORT=8080
ENV JAVA_OPTS="-Xms128m -Xmx256m -XX:+UseSerialGC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# Run Spring Boot app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
