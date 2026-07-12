# Build stage: compile and package with Maven on JDK 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first so code changes don't re-download everything.
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
COPY src ./src
RUN mvn -q -e -B clean package -DskipTests

# Run stage: slim JRE with just the built jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
