# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-XX:TieredStopAtLevel=1 -Xmx512m"
COPY pom.xml ./
COPY services services
COPY infra infra
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl infra/gateway -am -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/infra/gateway/target/gateway-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
