FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY bubble-ai-code-common/pom.xml bubble-ai-code-common/pom.xml
COPY bubble-ai-code-model/pom.xml bubble-ai-code-model/pom.xml
COPY bubble-ai-code-client/pom.xml bubble-ai-code-client/pom.xml
COPY bubble-ai-code-user/pom.xml bubble-ai-code-user/pom.xml
COPY . .

RUN mvn -B -DskipTests -pl bubble-ai-code-user -am package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=builder /workspace/bubble-ai-code-user/target/bubble-ai-code-user-*.jar /app/app.jar

EXPOSE 8124 50051
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
