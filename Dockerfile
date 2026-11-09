FROM eclipse-temurin:21-jdk-jammy AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.npm \
    --mount=type=cache,target=/src/flink-platform-ui/frontend/node_modules \
    VUE_APP_BASE_API=/ ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /src/flink-platform-web/target/flink-platform-web-*.jar /app/flink-platform-web.jar

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-Dspring.profiles.active=docker", "-jar", "/app/flink-platform-web.jar"]
