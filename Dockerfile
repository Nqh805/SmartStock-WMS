# Dùng máy ảo Maven kết hợp Eclipse Temurin Java 17 để build code
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests

# Dùng máy ảo Amazon Corretto Java 17 siêu nhẹ để chạy file Jar (Thay thế cho openjdk cũ)
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]