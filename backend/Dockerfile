FROM openjdk:20-jdk-slim
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw dependency:go-offline
EXPOSE 8080
CMD ["./mvnw", "spring-boot:run"]