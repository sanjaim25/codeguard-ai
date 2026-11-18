FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>google-maven-central</id><name>GCS Maven Central mirror</name><url>https://maven-central.storage-download.googleapis.com/maven2</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>\n' > /root/.m2/settings.xml
RUN mvn -B -ntp -Daether.connector.http.retryHandler.count=5 package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S codeguard && adduser -S codeguard -G codeguard
WORKDIR /app
COPY --from=build /build/target/codeguard-api-*.jar app.jar
USER codeguard
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
