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

# russian locale
RUN apt-get install --reinstall -y locales
RUN sed -i 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen
RUN locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US
ENV LC_ALL en_US.UTF-8


RUN echo "Copying Mirror..."
COPY app/build/libs/app-all.jar mirror.jar
COPY app/build/libs/app-all.jar mirror-child.jar
COPY config.yaml .
RUN pwd && ls -la && pwd


WORKDIR /home
CMD ["java", "-jar", "/mirror.jar", "/home/config.yaml"]
