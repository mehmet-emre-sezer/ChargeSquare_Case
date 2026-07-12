# Derleme aşaması: JDK 21 üzerinde Maven ile derle ve paketle.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Önce bağımlılıkları önbelleğe al ki kod değişikliğinde hepsi tekrar inmesin.
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline
COPY src ./src
RUN mvn -q -e -B clean package -DskipTests

# Çalıştırma aşaması: sadece derlenen jar'ı içeren ince JRE.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
