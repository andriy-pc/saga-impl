FROM openjdk:8-jdk-alpine
COPY ./target/order-service.jar order-service.jar
EXPOSE 5001
ENTRYPOINT ["java","-jar","order-service.jar"]