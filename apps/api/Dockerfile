FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY gradle.properties gradle.properties

RUN sed -i 's/\r$//' ./gradlew && chmod +x ./gradlew
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew dependencies --no-daemon > /tmp/gradle-dependencies.txt || \
    (cat /tmp/gradle-dependencies.txt && exit 1); \
    rm /tmp/gradle-dependencies.txt

COPY src src

RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S timearchive && adduser -S timearchive -G timearchive

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

USER timearchive

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
