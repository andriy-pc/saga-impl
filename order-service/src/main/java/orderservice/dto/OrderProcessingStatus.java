package orderservice.dto;

import common.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderProcessingStatus {

  public OrderProcessingStatus(OrderStatus orderStatus) {
    this.orderStatus = orderStatus;
  }

  private OrderStatus orderStatus;

  private String message;
}
