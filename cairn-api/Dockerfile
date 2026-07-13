# Build context is the repo root (see docker-compose.yml), because cairn-api is one
# module of a multi-module Gradle build that also compiles cairn-vcs and
# cairn-transfer as project dependencies (settings.gradle) - Gradle needs the whole
# tree, not just cairn-api/, to resolve those.
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY cairn-vcs ./cairn-vcs
COPY cairn-transfer ./cairn-transfer
COPY cairn-api ./cairn-api

# Tests already gate every commit (CLAUDE.md's "keep the tree green" rule) before
# this image is ever built; skipped here only to keep image builds fast, not as a
# substitute for ./gradlew build in CI or locally.
RUN chmod +x gradlew && ./gradlew :cairn-api:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/cairn-api/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
