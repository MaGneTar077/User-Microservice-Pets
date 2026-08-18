# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -Dmaven.test.skip=true


# Etapa 2: Runtime
FROM eclipse-temurin:17-jre-alpine-3.23

WORKDIR /app

RUN apk update && \
    apk upgrade --no-cache && \
    apk add --no-cache \
        sqlite-libs>=3.53.4-r0 \
        p11-kit>=0.26.2-r0 \
        p11-kit-trust>=0.26.2-r0 \
        expat>=2.8.2-r0

RUN addgroup -S spring && \
    adduser -S spring -G spring

COPY --from=build /app/target/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]