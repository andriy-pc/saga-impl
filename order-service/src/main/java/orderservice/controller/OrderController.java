package orderservice.controller;

import common.dto.OrderDTO;
import lombok.RequiredArgsConstructor;
import orderservice.dto.OrderProcessingStatus;
import orderservice.service.OrderProcessingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderProcessingService orderProcessingService;

  @PostMapping
  public OrderProcessingStatus createOrder(@RequestBody OrderDTO orderDTO) {
    return orderProcessingService.createOrder(orderDTO);
  }
}
