import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.spring") version "2.4.0"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.timearchive"
version = "0.0.1-SNAPSHOT"
description = "Time Archive backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("software.amazon.awssdk:s3:2.25.0")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.mockk:mockk:1.14.6")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
