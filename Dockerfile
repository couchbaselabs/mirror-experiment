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

RUN apt update && apt install -y man-db tree
RUN apt-get update && apt-get install -y tzdata && \
    ln -fs /usr/share/zoneinfo/America/New_York /etc/localtime && \
    echo "America/New_York" > /etc/timezone


RUN echo "Copying Mirror..."
COPY app/build/libs/app-all.jar mirror.jar
COPY config.yaml .


WORKDIR /memory
CMD ["java", "-jar", "/mirror.jar", "/config.yaml"]
