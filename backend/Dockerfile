# Producao (Render). Nao usado pelo docker-compose.yml de dev — aquele roda
# Postgres/Redis apenas; o backend em dev sobe via ./mvnw spring-boot:run.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/*.jar app.jar
USER spring

# Render injeta PORT em runtime; server.port do application.yml (8080) e o
# fallback local. --server.port como argumento sobrescreve o yml sem exigir
# mudanca no arquivo de configuracao.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
