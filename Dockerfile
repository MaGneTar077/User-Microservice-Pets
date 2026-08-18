# Etapa 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

# Copiar configuración de Maven
COPY pom.xml .

# Descargar dependencias
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar y empaquetar sin ejecutar tests
RUN mvn clean package -Dmaven.test.skip=true


# Etapa 2: Runtime
FROM eclipse-temurin:17-jre-alpine-3.23

WORKDIR /app

# Actualizar paquetes del sistema Alpine
RUN apk update && \
    apk upgrade --no-cache

# Crear usuario no-root
RUN addgroup -S spring && \
    adduser -S spring -G spring

# Copiar JAR
COPY --from=build /app/target/*.jar app.jar

# Cambiar a usuario no-root
USER spring:spring

# Puerto de la aplicación
EXPOSE 8080

# Configuración JVM
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Ejecutar aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]