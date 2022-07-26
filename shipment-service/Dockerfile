FROM openjdk:8-jdk-alpine
COPY ./target/shipment-service.jar shipment-service.jar
EXPOSE 5002
ENTRYPOINT ["java","-jar","shipment-service.jar"]