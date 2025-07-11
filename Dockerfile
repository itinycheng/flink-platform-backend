FROM openjdk:21-jdk-slim

COPY ./flink-platform-web/target/flink-platform-web-*.jar /app/flink-platform-web.jar

WORKDIR /app

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "-Dspring.profiles.active=docker", "/app/flink-platform-web.jar"]
