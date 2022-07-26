FROM openjdk:8-jdk-alpine
COPY ./target/warehouse-service.jar warehouse-service.jar
EXPOSE 5004
ENTRYPOINT ["java","-jar","warehouse-service.jar"]