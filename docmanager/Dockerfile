FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY docmanager/pom.xml docmanager/mvnw* ./
COPY docmanager/.mvn .mvn

RUN ./mvnw -B -ntp dependency:go-offline || true

COPY proto/ /proto/
COPY docmanager/src src

RUN ./mvnw -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

RUN useradd -u 1000 -m appuser
USER appuser

WORKDIR /app

COPY --from=builder /app/target/*.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","/app/app.jar"]
