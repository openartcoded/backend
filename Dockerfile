FROM maven:3.8.4-openjdk-17 as builder
LABEL maintainer="contact@bittich.be"

WORKDIR /app

COPY pom.xml .
COPY ./artcoded/pom.xml ./artcoded/pom.xml

RUN mvn -B dependency:resolve-plugins dependency:resolve

COPY ./artcoded/src ./artcoded/src

RUN mvn package -DskipTests

FROM ibm-semeru-runtimes:open-17-jre
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
RUN wget https://github.com/wkhtmltopdf/packaging/releases/download/0.12.6-1/wkhtmltox_0.12.6-1.focal_amd64.deb
RUN dpkg -i wkhtmltox_0.12.6-1.focal_amd64.deb

# install mongodb tools
RUN apt-get install -y gnupg
RUN wget -qO - https://www.mongodb.org/static/pgp/server-5.0.asc | apt-key add -
RUN echo "deb http://repo.mongodb.org/apt/debian buster/mongodb-org/5.0 main" | tee /etc/apt/sources.list.d/mongodb-org-5.0.list
RUN apt-get update
RUN apt-get install -y mongodb-org-tools

WORKDIR /app

COPY --from=builder /app/artcoded/target/api-backend.jar ./app.jar

ENTRYPOINT [ "java", "-Xtune:virtualized", "-Xshareclasses:cacheDir=/opt/shareclasses", "-jar","/app/app.jar"]

