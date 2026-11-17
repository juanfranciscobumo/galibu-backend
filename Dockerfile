FROM docker.io/library/eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew
RUN ./gradlew test bootJar --no-daemon

FROM docker.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/galibu-backend-0.1.0.jar app.jar
COPY --from=build /app/build/test-results /app/test-results
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]