FROM maven:3-eclipse-temurin-21 as builder
LABEL maintainer="contact@bittich.be"

WORKDIR /app

COPY pom.xml .
COPY ./event/pom.xml ./event/pom.xml
COPY ./artcoded/pom.xml ./artcoded/pom.xml

RUN mvn verify --fail-never

COPY ./artcoded/src ./artcoded/src
COPY ./event/src ./event/src

COPY ./install-wkhtmltopdf.sh ./install-wkhtmltopdf.sh

RUN mvn package -DskipTests

FROM ibm-semeru-runtimes:open-21-jre-jammy
LABEL maintainer="contact@bittich.be"

RUN apt-get update

# Set timezone
ENV CONTAINER_TIMEZONE 'Europe/Brussels'
RUN apt-get update && apt-get install -y tzdata && \
  rm /etc/localtime && \
  ln -snf /usr/share/zoneinfo/$CONTAINER_TIMEZONE /etc/localtime &&  \
  echo $CONTAINER_TIMEZONE > /etc/timezone && \
  dpkg-reconfigure -f noninteractive tzdata && \
  apt-get clean

# install wkhtmltopdf
RUN apt-get install -y  wget
RUN apt-get install -y  fontconfig libjpeg-turbo8 libssl-dev libxext6 libxrender-dev xfonts-base xfonts-75dpi
RUN chmod +x install-wkhtmltopdf.sh && ./install-wkhtmltopdf.sh

# install mongodb tools
RUN apt-get install -y gnupg
RUN wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | apt-key add -
RUN echo "deb http://repo.mongodb.org/apt/debian buster/mongodb-org/6.0 main" | tee /etc/apt/sources.list.d/mongodb-org-6.0.list
RUN apt-get update
RUN apt-get install -y mongodb-org-tools

WORKDIR /app
COPY --from=builder /app/artcoded/target/api-backend.jar ./api-backend.jar


# add  "--log.file=/tmp/truffle.log" if it's too verbose
ENTRYPOINT [ "java", "--enable-preview","-Xtune:virtualized","-XX:+CompactStrings", "-Xshareclasses:cacheDir=/opt/shareclasses", "-jar","/app/api-backend.jar"]

