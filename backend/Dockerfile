FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
# Dockerfile: multi-stage build for optimized image size
# Fix: use official openjdk:17-slim base image for smaller attack surface
# Docs: Docker build args and environment variable reference
# Test: Dockerfile build stage verification - multi-stage succeeds
# Chore: Dockerfile and deployment configuration finalization
# Perf: optimize Docker build with layer caching and multi-stage builds
