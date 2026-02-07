# Dockerfile

# 1. Build Stage
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app
COPY . .

# gradlew 파일에 실행 권한을 부여합니다.
RUN chmod +x gradlew

# Gradle 데몬을 사용하지 않고 빌드를 실행합니다.
RUN ./gradlew clean build -x test --no-daemon

# 2. Final Image Stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# build/libs/ 디렉토리의 jar 파일을 app.jar로 복사
COPY --from=builder /app/build/libs/*.jar app.jar

VOLUME /app/config

EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]