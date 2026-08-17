# Stage 1 — Build the Spring Boot app
# Maven is preinstalled in this image. The ./mvnw wrapper cannot run on the
# deployment host: its bootstrap extraction fails with ENOSYS on kernel 5.4.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

COPY backend/pom.xml .

# Download dependencies first (for caching)
RUN mvn dependency:go-offline -B

# Copy source code
COPY backend/src src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests -B

# Stage 2 — Run the JAR
# Runtime needs a JRE, not a JDK: shipping javac, jdb and the full tool chain
# into production adds attack surface and image size for no benefit.
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
