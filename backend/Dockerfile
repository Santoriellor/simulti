# Stage 1 — Build the Spring Boot app
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper and .mvn folder
COPY backend/mvnw .
COPY backend/.mvn .mvn
COPY backend/pom.xml .

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies first (for caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY backend/src src

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests -B

# Stage 2 — Run the JAR
FROM eclipse-temurin:25-jdk
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
