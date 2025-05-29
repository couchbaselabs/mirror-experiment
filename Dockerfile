# Use Ubuntu as the base image
FROM ubuntu:latest

# Update Ubuntu packages
RUN apt-get update -y

# set JAVA_VERSION
ARG JAVA_VERSION=openjdk-17-jdk

# Install Java
RUN apt-get install -y $JAVA_VERSION

# Setup JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-19-openjdk-amd64


COPY app loopus_src
COPY app/build/libs/app-all.jar loopus.jar
COPY config.yaml .

RUN apt install -y man-db

CMD ["java", "-jar", "loopus.jar", "config.yaml"]
