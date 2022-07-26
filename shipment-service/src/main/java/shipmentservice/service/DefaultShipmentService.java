package shipmentservice.service;

import common.enums.OrderStatus;
import common.enums.ProcessingStatus;
import common.event.OrderProcessingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import shipmentservice.connector.ShipmentAPIConnector;
import shipmentservice.exception.UnshippedOrderException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultShipmentService implements ShipmentService {

  @Value("${config.kafka.order-processing-topic}")
  private String orderProcessingTopicName;

  private final ShipmentAPIConnector shipmentAPIConnector;
  private final KafkaTemplate<String, OrderProcessingEvent> kafkaTemplate;

  @KafkaListener(
      topics = {"order-processing"},
      groupId = "4")
  public void listenToOrderProcessingEvent(OrderProcessingEvent orderProcessingEvent) {
    if (!orderProcessingEvent.processed() && orderProcessingEvent.toProcessForShipment()) {
      log.info(
          "Order should be applied for shipment shipped for user with id: {} (event ID: {})",
          orderProcessingEvent.getOrderDTO().getUserId(),
          orderProcessingEvent.getUuid());
      processOrder(orderProcessingEvent);
    }
  }

  private void processOrder(OrderProcessingEvent orderProcessingEvent) {
    log.info("Order processing is in progress");
    try {
      shipmentAPIConnector.shipOrder(orderProcessingEvent.getOrderDTO());
      orderProcessingEvent.setOrderStatus(OrderStatus.CREATED);
      orderProcessingEvent.setShipmentProcessingStatus(ProcessingStatus.SUCCESS);
      kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
      log.info("Order event ({}) was processed successfully", orderProcessingEvent.getUuid());
    } catch (UnshippedOrderException e) {
      emitTransactionRevertEvent(orderProcessingEvent);
    }
  }

  private void emitTransactionRevertEvent(OrderProcessingEvent orderProcessingEvent) {
    log.warn("Order processing failed!");
    orderProcessingEvent.setOrderStatus(OrderStatus.CANCELLED);
    orderProcessingEvent.setShipmentProcessingStatus(ProcessingStatus.REVERT);
    orderProcessingEvent.addMessage(
        "Exception happened during applying order for the shipment. "
            + "Shipment provider caused this issue");
    kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
  }
}
