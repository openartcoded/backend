FROM maven:3-eclipse-temurin-22 as builder
LABEL maintainer="contact@bittich.be"

WORKDIR /app

COPY pom.xml .
COPY ./event/pom.xml ./event/pom.xml
COPY ./artcoded/pom.xml ./artcoded/pom.xml

RUN mvn verify --fail-never

COPY ./artcoded/src ./artcoded/src
COPY ./event/src ./event/src


RUN mvn package -DskipTests

# FROM ibm-semeru-runtimes:open-22-jre-jammy
FROM eclipse-temurin:22-jre-jammy
LABEL maintainer="contact@bittich.be"

RUN apt-get update

COPY ./install-wkhtmltopdf.sh ./install-wkhtmltopdf.sh
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
RUN bash /install-wkhtmltopdf.sh

# install mongodb tools
RUN apt-get install -y gnupg curl
RUN curl -fsSL https://www.mongodb.org/static/pgp/server-6.0.asc | \
  gpg -o /usr/share/keyrings/mongodb-server-6.0.gpg \
  --dearmor
RUN echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-6.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/6.0 multiverse" |  tee /etc/apt/sources.list.d/mongodb-org-6.0.list
RUN apt-get update
RUN apt-get install -y mongodb-org-tools

WORKDIR /app
COPY --from=builder /app/artcoded/target/api-backend.jar ./api-backend.jar
ENV JAVA_OPTS "-Xmx1024m -Xms256m -XX:InitialRAMPercentage=5 -XX:MaxRAMPercentage=5 --enable-preview -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI"

# add  "--log.file=/tmp/truffle.log" if it's too verbose
#ENTRYPOINT [ "java", "--enable-preview","-Xtune:virtualized","-XX:+CompactStrings", "-Xshareclasses:cacheDir=/opt/shareclasses", "-jar","/app/api-backend.jar"]
ENTRYPOINT [ "sh", "-c","java $JAVA_OPTS  -jar /app/api-backend.jar"]

