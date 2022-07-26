FROM openjdk:8-jdk-alpine
COPY ./target/user-service.jar user-service.jar
EXPOSE 5003
ENTRYPOINT ["java","-jar","user-service.jar"]