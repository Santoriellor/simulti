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

# Run unprivileged. Port 8080 is above 1024, so no capability is required.
# /app/logs must exist and be writable: application.yml sets
# logging.file.name=logs/app.log, which resolves under this working directory.
# getent guards: these base images already ship a default user at uid/gid 1000,
# so a bare groupadd/useradd exits non-zero and fails the build. The numeric
# uid is what matters - /app must be owned by 1000 either way.
RUN (getent group 1000 || groupadd --gid 1000 app) \
    && (getent passwd 1000 || useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin app) \
    && mkdir -p /app/logs \
    && chown -R 1000:1000 /app

USER 1000:1000

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
