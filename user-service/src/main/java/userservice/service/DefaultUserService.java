package userservice.service;

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
import userservice.connector.ProductServiceConnector;
import userservice.dto.UserDTO;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultUserService implements UserService {

  private static final Integer FIRST_ATTEMPT = 0;
  private static final Integer MAX_REVERT_ATTEMPTS = 5;

  @Value("${config.kafka.order-processing-topic}")
  private String orderProcessingTopicName;

  private final ProductServiceConnector productConnector;

  private final KafkaTemplate<String, OrderProcessingEvent> kafkaTemplate;

  private static final Map<Integer, UserDTO> ID_PER_USER =
      new HashMap<Integer, UserDTO>() {
        {
          put(1, new UserDTO(1, BigDecimal.valueOf(100)));
          put(2, new UserDTO(2, BigDecimal.valueOf(15_000)));
        }
      };

  @Override
  public List<UserDTO> getAllUsers() {
    return new ArrayList<>(ID_PER_USER.values());
  }

  @KafkaListener(
      topics = {"order-processing"},
      groupId = "2")
  private void listenForOrderProcessingEvent(OrderProcessingEvent orderProcessingEvent) {
    if (!orderProcessingEvent.processed() && orderProcessingEvent.toProcessForUser()) {
      log.info(
          "Order should be processed for user with id: {} (event ID: {})",
          orderProcessingEvent.getOrderDTO().getUserId(),
          orderProcessingEvent.getUuid());
      processOrder(orderProcessingEvent);
    } else if (mustBeReverted(orderProcessingEvent)) {
      log.warn("Order event ({}) should be reverted", orderProcessingEvent.getUuid());
      BigDecimal orderPrice = calculateOrderPrice(orderProcessingEvent);
      revertOrderProcessingWithRetry(orderProcessingEvent, orderPrice, FIRST_ATTEMPT);
      emitTransactionRevertEvent(orderProcessingEvent);
    }
  }

  private void processOrder(OrderProcessingEvent orderProcessingEvent) {
    try {
      log.info("Order processing is in progress");
      BigDecimal orderPrice = calculateOrderPrice(orderProcessingEvent);
      if (validateUser(orderProcessingEvent, orderPrice)
          && startBalanceDecreasingTransaction(orderProcessingEvent.getOrderDTO(), orderPrice)) {
        emitTransactionSuccessEvent(orderProcessingEvent);
      } else {
        emitTransactionRevertEvent(orderProcessingEvent);
      }
    } catch (Exception e) {
      log.error(
          "Exception occurred during processing order for user: {}. Exception: ",
          orderProcessingEvent.getOrderDTO().getUserId(),
          e);
      orderProcessingEvent.addMessage("Issue occurred during balance decreasing!");
      emitTransactionRevertEvent(orderProcessingEvent);
    }
  }

  private void emitTransactionSuccessEvent(OrderProcessingEvent orderProcessingEvent) {
    orderProcessingEvent.setUserProcessingStatus(ProcessingStatus.SUCCESS);
    orderProcessingEvent.addMessage("Order was successfully processed for user");
    kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
    log.info("Order event ({}) was processed successfully", orderProcessingEvent.getUuid());
  }

  private void emitTransactionRevertEvent(OrderProcessingEvent orderProcessingEvent) {
    orderProcessingEvent.setOrderStatus(OrderStatus.CANCELLED);
    orderProcessingEvent.setUserProcessingStatus(ProcessingStatus.REVERT);
    kafkaTemplate.send(orderProcessingTopicName, orderProcessingEvent);
  }

  private BigDecimal calculateOrderPrice(OrderProcessingEvent orderProcessingEvent) {
    ProductDTO orderedProduct =
        productConnector.getProductById(orderProcessingEvent.getOrderDTO().getProductId());
    return orderedProduct
        .getPrice()
        .multiply(BigDecimal.valueOf(orderProcessingEvent.getOrderDTO().getOrderedQty()));
  }

  private boolean validateUser(OrderProcessingEvent orderProcessingEvent, BigDecimal orderPrice) {
    UserDTO orderUser = getUserFromMap(orderProcessingEvent.getOrderDTO());

    return Objects.nonNull(orderUser) && validateUserBalance(orderPrice, orderUser);
  }

  private boolean validateUserBalance(BigDecimal orderPrice, UserDTO orderUser) {
    boolean userBalanceIsSufficient = orderUser.getBalance().compareTo(orderPrice) >= 0;
    if (!userBalanceIsSufficient) {
      log.warn(
          "User has insufficient balance. Order price: {}, user balance: {}",
          orderPrice,
          orderUser.getBalance());
    }
    return userBalanceIsSufficient;
  }

  private UserDTO getUserFromMap(OrderDTO orderDTO) {
    Integer orderUserId = orderDTO.getUserId();
    return ID_PER_USER.get(orderUserId);
  }

  @Transactional
  boolean startBalanceDecreasingTransaction(OrderDTO orderDTO, BigDecimal orderPrice) {
    try {
      UserDTO orderUser = getUserFromMap(orderDTO);
      orderUser.setBalance(orderUser.getBalance().subtract(orderPrice));
      return true;
    } catch (Exception e) {
      log.error(
          "Exception occurred during qty decreasing transaction. "
              + "The recovery transaction will be applied. "
              + "Exception: ",
          e);
      return false;
    }
  }

  private boolean mustBeReverted(OrderProcessingEvent orderProcessingEvent) {
    return orderProcessingEvent.getWarehouseProcessingStatus().equals(ProcessingStatus.REVERT)
        && !orderProcessingEvent.getUserProcessingStatus().equals(ProcessingStatus.REVERT);
  }

  private void revertOrderProcessingWithRetry(
      OrderProcessingEvent orderProcessingEvent, BigDecimal orderPrice, int attempt) {
    log.info("Revert transaction is in progress. Attempt: {}/{}", attempt, MAX_REVERT_ATTEMPTS);
    if (attempt >= MAX_REVERT_ATTEMPTS) {
      throw new RuntimeException(
          "Reverting order processing event failed for event with ID: "
              + orderProcessingEvent.getUuid());
    } else {
      try {
        startBalanceIncreasingTransaction(orderProcessingEvent.getOrderDTO(), orderPrice);
        log.info("Revert transaction finished successfully");
      } catch (Exception e) {
        log.error(
            "Exception occurred during the revert transaction. "
                + "The logic will try to revert it again. Attempt: {}/{}"
                + "Exception: ",
            attempt,
            MAX_REVERT_ATTEMPTS,
            e);
        revertOrderProcessingWithRetry(orderProcessingEvent, orderPrice, attempt);
      }
    }
  }

  @Transactional
  void startBalanceIncreasingTransaction(OrderDTO orderDTO, BigDecimal orderPrice) {
    try {
      log.info("Balance increasing transaction in progress");
      UserDTO orderUser = getUserFromMap(orderDTO);
      orderUser.setBalance(orderUser.getBalance().add(orderPrice));
      log.info("Balance increasing transaction finished successfully");
    } catch (Exception e) {
      log.error("Exception occurred during user's balance increasing transaction. Exception: ", e);
    }
  }
}
