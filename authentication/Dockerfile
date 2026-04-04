FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY pom.xml mvnw* ./
COPY .mvn .mvn

RUN ./mvnw -B -ntp dependency:go-offline || true

COPY src src

RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

RUN useradd -u 1000 -m appuser
USER appuser

WORKDIR /app

COPY --from=builder /app/target/*.jar /app/app.jar

EXPOSE 8082

ENTRYPOINT ["java","-jar","/app/app.jar"]
