plugins {
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.0"
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.spring") version "1.9.22"
}

group = "until.the.eternity"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Spring Boot Web (REST API)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring Security + JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // OAuth2 Client
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // JPA + DB Driver (MySQL 예시)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Spring Docs
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Kotlin support
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Redis (캐싱 및 세션 관리용)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Lombok (Optional - 편리한 getter/setter)
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")

    // Flyway db migration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // Docker Compose
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // AWS
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3:3.1.0")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.h2database:h2")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testImplementation("org.mockito:mockito-core:4.5.1")
    testImplementation("org.mockito:mockito-junit-jupiter:4.5.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<Copy>("copyYml") {
    if (System.getenv("CI") == "true") {
        println("CI environment detected. Copying application-sample.yml to application.yml")
        from("src/main/resources")
        include("application-sample.yml")
        into("src/main/resources")
        rename {
            it.replace("-sample", "")
        }
    }
}

tasks.named("bootJar") {
    dependsOn(tasks.named("copyYml"))
}
