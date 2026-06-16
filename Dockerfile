FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY gradle.properties gradle.properties
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S timearchive && adduser -S timearchive -G timearchive

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

USER timearchive

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
