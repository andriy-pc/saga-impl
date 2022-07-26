package orderservice.service;

import common.dto.OrderDTO;
import common.enums.OrderStatus;
import common.event.OrderProcessingEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import orderservice.dto.OrderProcessingStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultOrderProcessingService implements OrderProcessingService {

  private static final int MAX_ATTEMPTS_TO_WAIT_FOR_ORDER_PROCESSING = 30;
  private static final int FIRST_WAITING_ATTEMPT = 1;
  private static final int ORDER_PROCESSING_WAITING_TIME_SEC = 1;

  @Value("${config.kafka.order-processing-topic}")
  private String orderProcessingTopicName;

  private final KafkaTemplate<String, OrderProcessingEvent> kafkaTemplate;

  private final Map<UUID, OrderProcessingEvent> processedOrderEvents = new HashMap<>();

  @Override
  public OrderProcessingStatus createOrder(OrderDTO orderDTO) {
    try {
      OrderProcessingEvent orderProcessingEvent = buildOrderProcessingEvent(orderDTO);
      log.info("Order creation in progress (event ID: {})...", orderProcessingEvent.getUuid());
      kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
      OrderProcessingEvent orderProcessingResult =
          waitForOrderProcessingResult(orderProcessingEvent.getUuid(), FIRST_WAITING_ATTEMPT);
      log.info(
          "Order creation finished successfully (event ID: {})", orderProcessingEvent.getUuid());
      return new OrderProcessingStatus(
          orderProcessingResult.getOrderStatus(),
          orderProcessingResult.getProcessingResultMessage());
    } catch (Exception e) {
      log.error(
          "Exception occurred during create order flow ({}, {}, {}). Exception: ",
          orderDTO.getUserId(),
          orderDTO.getProductId(),
          orderDTO.getOrderedQty(),
          e);
      return new OrderProcessingStatus(OrderStatus.CANCELLED);
    }
  }

  private OrderProcessingEvent buildOrderProcessingEvent(OrderDTO orderDTO) {
    OrderProcessingEvent orderProcessingEvent = new OrderProcessingEvent();
    orderProcessingEvent.setOrderDTO(orderDTO);
    orderProcessingEvent.setOrderStatus(OrderStatus.REQUESTED);
    return orderProcessingEvent;
  }

  @KafkaListener(
      topics = {"order-processing"},
      groupId = "1")
  public void receiveOrderProcessingEvent(OrderProcessingEvent orderProcessingEvent) {
    if (orderProcessingEvent.processed()) {
      processedOrderEvents.put(orderProcessingEvent.getUuid(), orderProcessingEvent);
    }
  }

  private OrderProcessingEvent waitForOrderProcessingResult(
      UUID orderProcessingEventId, int attemptCount) {
    if (attemptCount > MAX_ATTEMPTS_TO_WAIT_FOR_ORDER_PROCESSING) {
      throw new RuntimeException(
          "Waiting attempts were exceeded for order event: " + orderProcessingEventId);
    }
    OrderProcessingEvent processedEvent = processedOrderEvents.get(orderProcessingEventId);
    if (Objects.nonNull(processedEvent)) {
      processedOrderEvents.remove(orderProcessingEventId);
      return processedEvent;
    } else {
      try {
        TimeUnit.SECONDS.sleep(ORDER_PROCESSING_WAITING_TIME_SEC);
        return waitForOrderProcessingResult(orderProcessingEventId, ++attemptCount);
      } catch (InterruptedException e) {
        throw new RuntimeException(
            String.format(
                "Exception occurred during waiting for order processing event (uuid: %s) to be processed by the system"
                    + "Exception: %s",
                orderProcessingEventId, Arrays.toString(e.getStackTrace())));
      }
    }
  }
}
