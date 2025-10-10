FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/chaty.jar app.jar
RUN apt-get update && apt-get install -y curl
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
