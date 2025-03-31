FROM openjdk:21-jdk-alpine
RUN mkdir -p /opt/bauta
COPY target/bauta.zip /opt/bauta/bauta.zip
RUN apt-get update \
    && apt-get install -y unzip
RUN unzip -o /opt/bauta/bauta.zip -d /opt/bauta
WORKDIR /opt/bauta/
EXPOSE 8080
ENTRYPOINT ["bash","bin/startBauta.sh", "-h", "/opt/bauta/home"]