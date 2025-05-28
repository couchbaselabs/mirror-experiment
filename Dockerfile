FROM amazoncorretto:17-alpine-jdk

COPY app/build/libs/app-all.jar loopus.jar
COPY config.yaml .
CMD ["java", "-jar", "loopus.jar", "config.yaml"]
