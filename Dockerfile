FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
COPY data/trained-model.txt ./data/trained-model.txt
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/data/trained-model.txt ./data/trained-model.txt
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]