FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY bubble-ai-code-common/pom.xml bubble-ai-code-common/pom.xml
COPY bubble-ai-code-model/pom.xml bubble-ai-code-model/pom.xml
COPY bubble-ai-code-client/pom.xml bubble-ai-code-client/pom.xml
COPY bubble-ai-code-ai/pom.xml bubble-ai-code-ai/pom.xml
COPY bubble-ai-code-app/pom.xml bubble-ai-code-app/pom.xml
COPY bubble-ai-code-user/pom.xml bubble-ai-code-user/pom.xml
COPY bubble-ai-code-screenshot/pom.xml bubble-ai-code-screenshot/pom.xml
COPY . .

RUN mvn -B -DskipTests -pl bubble-ai-code-app -am package

FROM eclipse-temurin:21-jre-jammy

ENV DEBIAN_FRONTEND=noninteractive
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV NPM_CONFIG_CACHE=/app/tmp/.npm-cache

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && npx -y playwright@1.44.0 install --with-deps chromium \
    && npm cache clean --force \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN mkdir -p /app/tmp
COPY --from=builder /workspace/bubble-ai-code-app/target/bubble-ai-code-app-*.jar /app/app.jar

EXPOSE 8125 50053
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
