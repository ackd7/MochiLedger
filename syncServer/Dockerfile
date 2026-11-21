FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY syncServer ./syncServer
RUN chmod +x gradlew && ./gradlew :syncServer:installDist --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/syncServer/build/install/syncServer ./
ENV MOCHI_PORT=8787
ENV MOCHI_DATA_DIR=/data
ENV MOCHI_SYNC_TOKEN=change-me-in-production
VOLUME ["/data"]
EXPOSE 8787
ENTRYPOINT ["./bin/syncServer"]
