package orderservice.service;

import common.dto.OrderDTO;
import orderservice.dto.OrderProcessingStatus;

public interface OrderProcessingService {

  OrderProcessingStatus createOrder(OrderDTO orderDTO);
}
