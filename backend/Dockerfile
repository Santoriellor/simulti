# Stage 1 — Build the Spring Boot app
FROM openjdk:20-jdk-slim AS build
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN ./mvnw -q -e -DskipTests clean package

# Stage 2 — Run the JAR
FROM openjdk:20-jdk-slim
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
