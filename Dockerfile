# Dockerfile

# 1. Build Stage
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app
COPY . .

# Build from the tracked, environment-driven template, never a host credential file.
COPY src/main/resources/application-sample.yml src/main/resources/application.yml

# gradlew 파일에 실행 권한을 부여합니다.
RUN chmod +x gradlew

# Package only the executable JAR; tests run separately through verify.
RUN ./gradlew clean bootJar --no-daemon

# 2. Final Image Stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# build/libs/ 디렉토리의 jar 파일을 app.jar로 복사
COPY --from=builder /app/build/libs/*.jar app.jar

VOLUME /app/config

EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
