# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -Dmaven.test.skip=true dependency:go-offline
COPY src ./src
RUN mvn -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache mariadb-client mariadb-connector-c tzdata \
 && addgroup -S app && adduser -S app -G app

ENV TZ=Asia/Ho_Chi_Minh
WORKDIR /app

COPY --from=build /src/target/*.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh \
 && chmod +x /app/docker-entrypoint.sh

USER app
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
