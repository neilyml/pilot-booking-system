# syntax=docker/dockerfile:1.7

FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/*.jar application.jar

FROM eclipse-temurin:25-jre-ubi10-minimal AS runtime

WORKDIR /app

COPY --from=build --chown=10001:10001 /workspace/application.jar ./application.jar

USER 10001:10001

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
