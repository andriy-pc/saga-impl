package warehouseservice.service;

import common.dto.OrderDTO;
import common.dto.ProductDTO;
import common.enums.OrderStatus;
import common.enums.ProcessingStatus;
import common.event.OrderProcessingEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultProductService implements ProductService {

  private static final Integer FIRST_ATTEMPT = 0;
  private static final Integer MAX_REVERT_ATTEMPTS = 5;

  @Value("${config.kafka.order-processing-topic}")
  private String orderProcessingTopicName;

  private static final Map<Integer, ProductDTO> PRODUCT_ID_PER_AVAILABLE_PRODUCT =
      new HashMap<Integer, ProductDTO>() {
        {
          put(1, new ProductDTO(1, "pen", 10, BigDecimal.valueOf(1.5)));
          put(2, new ProductDTO(2, "iPhone", 1, BigDecimal.valueOf(1500)));
        }
      };

  private final KafkaTemplate<String, OrderProcessingEvent> kafkaTemplate;

  @Override
  public List<ProductDTO> getAvailableProducts() {
    return new ArrayList<>(PRODUCT_ID_PER_AVAILABLE_PRODUCT.values());
  }

  @Override
  public ProductDTO getProductById(Integer productId) {
    return PRODUCT_ID_PER_AVAILABLE_PRODUCT.get(productId);
  }

  @KafkaListener(
      topics = {"order-processing"},
      groupId = "3")
  public void listenToOrderProcessingEvent(OrderProcessingEvent orderProcessingEvent) {
    if (!orderProcessingEvent.processed() && orderProcessingEvent.toProcessForWarehouse()) {
      log.info(
          "Order should be processed for product with id: {} (event ID: {})",
          orderProcessingEvent.getOrderDTO().getProductId(),
          orderProcessingEvent.getUuid());
      processOrder(orderProcessingEvent);
    } else if (mustBeReverted(orderProcessingEvent)) {
      log.warn("Order event ({}) should be reverted", orderProcessingEvent.getUuid());
      revertProductProcessingWithRetry(orderProcessingEvent, FIRST_ATTEMPT);
      orderProcessingEvent.addMessage("Reverting QTY decreasing");
      emitTransactionRevertEvent(orderProcessingEvent);
    }
  }

  private void processOrder(OrderProcessingEvent orderProcessingEvent) {
    log.info("Order processing is in progress");
    if (checkProductAvailability(orderProcessingEvent)
        && startQtyDecreasingTransaction(orderProcessingEvent.getOrderDTO())) {
      orderProcessingEvent.setWarehouseProcessingStatus(ProcessingStatus.SUCCESS);
      orderProcessingEvent.addMessage("Warehouse service successfully processed the order");
      kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
      log.info("Order event ({}) was processed successfully", orderProcessingEvent.getUuid());
    } else {
      log.warn("Order processing failed!");
      orderProcessingEvent.addMessage(
          "Issue occurred during qty decreasing! "
              + "That can be caused by ordered qty > available qty, or exception during transaction");
      emitTransactionRevertEvent(orderProcessingEvent);
    }
  }

  private void emitTransactionRevertEvent(OrderProcessingEvent orderProcessingEvent) {
    orderProcessingEvent.setWarehouseProcessingStatus(ProcessingStatus.REVERT);
    orderProcessingEvent.setOrderStatus(OrderStatus.CANCELLED);
    kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
  }

  private boolean checkProductAvailability(OrderProcessingEvent orderProcessingEvent) {
    Integer productId = orderProcessingEvent.getOrderDTO().getProductId();
    ProductDTO requestedProduct = PRODUCT_ID_PER_AVAILABLE_PRODUCT.get(productId);
    return Objects.nonNull(requestedProduct)
        && requestedProduct.getStockQty() >= orderProcessingEvent.getOrderDTO().getOrderedQty();
  }

  @Transactional
  boolean startQtyDecreasingTransaction(OrderDTO orderDTO) {
    try {
      Integer productId = orderDTO.getProductId();
      ProductDTO requestedProduct = PRODUCT_ID_PER_AVAILABLE_PRODUCT.get(productId);
      requestedProduct.setStockQty(requestedProduct.getStockQty() - orderDTO.getOrderedQty());
      return true;
    } catch (Exception e) {
      log.error(
          "Exception occurred during qty decreasing transaction. The recovery transaction will be applied");
      return false;
    }
  }

  private boolean mustBeReverted(OrderProcessingEvent orderProcessingEvent) {
    return orderProcessingEvent.getShipmentProcessingStatus().equals(ProcessingStatus.REVERT)
        && !orderProcessingEvent.getWarehouseProcessingStatus().equals(ProcessingStatus.REVERT);
  }

  private void revertProductProcessingWithRetry(
      OrderProcessingEvent orderProcessingEvent, int attempt) {
    log.info("Revert transaction is in progress. Attempt: {}/{}", attempt, MAX_REVERT_ATTEMPTS);
    if (attempt >= MAX_REVERT_ATTEMPTS) {
      throw new RuntimeException(
          "Reverting order processing event failed for event with ID: "
              + orderProcessingEvent.getUuid());
    } else {
      try {
        startQtyIncreasingTransaction(orderProcessingEvent.getOrderDTO());
      } catch (Exception e) {
        log.error(
            "Exception occurred during the revert transaction. "
                + "The logic will try to revert it again. Attempt: {}/{}"
                + "Exception: ",
            attempt,
            MAX_REVERT_ATTEMPTS,
            e);
        revertProductProcessingWithRetry(orderProcessingEvent, attempt);
      }
    }
  }

  @Transactional
  boolean startQtyIncreasingTransaction(OrderDTO orderDTO) {
    log.info("QTY increasing transaction in progress");
    try {
      Integer productId = orderDTO.getProductId();
      ProductDTO requestedProduct = PRODUCT_ID_PER_AVAILABLE_PRODUCT.get(productId);
      requestedProduct.setStockQty(requestedProduct.getStockQty() + orderDTO.getOrderedQty());
      log.info("QTY increasing transaction finished successfully");
      return true;
    } catch (Exception e) {
      log.error(
          "Exception occurred during product stock qty increasing transaction. Exception: ", e);
      return false;
    }
  }
}
