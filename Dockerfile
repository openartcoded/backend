FROM maven:3-eclipse-temurin-25 AS builder
LABEL maintainer="contact@bittich.be"

WORKDIR /app

COPY pom.xml .
COPY ./event/pom.xml ./event/pom.xml
COPY ./artcoded/pom.xml ./artcoded/pom.xml

RUN mvn verify --fail-never

COPY ./artcoded/src ./artcoded/src
COPY ./event/src ./event/src


RUN mvn package -DskipTests

#FROM ibm-semeru-runtimes:open-25-jre-jammy
FROM eclipse-temurin:25-jre-jammy
LABEL maintainer="contact@bittich.be"

RUN apt-get update

# Set timezone
ENV CONTAINER_TIMEZONE='Europe/Brussels'
RUN apt-get update && apt-get install -y tzdata && \
  rm /etc/localtime && \
  ln -snf /usr/share/zoneinfo/$CONTAINER_TIMEZONE /etc/localtime &&  \
  echo $CONTAINER_TIMEZONE > /etc/timezone && \
  dpkg-reconfigure -f noninteractive tzdata && \
  apt-get clean

# fonts used by chromium to render pdfs (Liberation is metric-compatible with Arial/Times/Courier)
RUN apt-get install -y --no-install-recommends fontconfig fonts-liberation fonts-dejavu-core

# install mongodb tools
RUN apt-get install -y gnupg curl
RUN curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | \
  gpg -o /usr/share/keyrings/mongodb-server-7.0.gpg \
  --dearmor
RUN echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" |  tee /etc/apt/sources.list.d/mongodb-org-7.0.list
RUN apt-get update
RUN apt-get install -y mongodb-org-tools

# headless chromium for pdf rendering (html -> pdf). Own layer, before the jar is copied: it stays cached until
# PLAYWRIGHT_VERSION changes. Only the Playwright CLI jars are downloaded (then removed); --with-deps installs the
# system libraries chromium needs (amd64 & arm64).
# PLAYWRIGHT_VERSION must match <playwright.version> in pom.xml (checked by DockerfilePlaywrightVersionTest)
ARG PLAYWRIGHT_VERSION=1.64.0
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN set -eux; \
  mkdir -p /tmp/playwright-cli; \
  for artifact in playwright driver driver-bundle; do \
    curl -fsSL -o "/tmp/playwright-cli/${artifact}.jar" \
      "https://repo1.maven.org/maven2/com/microsoft/playwright/${artifact}/${PLAYWRIGHT_VERSION}/${artifact}-${PLAYWRIGHT_VERSION}.jar"; \
  done; \
  java -cp "/tmp/playwright-cli/*" com.microsoft.playwright.CLI install --with-deps --only-shell chromium; \
  rm -rf /tmp/playwright-cli /var/lib/apt/lists/*
# never download a browser at runtime
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /app
COPY --from=builder /app/artcoded/target/api-backend.jar ./api-backend.jar

COPY docker-entrypoint.sh docker-entrypoint.sh
RUN chmod +x docker-entrypoint.sh

ENTRYPOINT [ "/app/docker-entrypoint.sh"]
