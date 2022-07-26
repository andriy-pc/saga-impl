package shipmentservice.connector;

import common.dto.OrderDTO;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import shipmentservice.exception.UnshippedOrderException;

@Service
@RequiredArgsConstructor
public class ShipmentAPIConnector {

  private final Random random;

  public boolean shipOrder(OrderDTO orderDTO) throws UnshippedOrderException {
    if (random.nextBoolean()) {
      throw new UnshippedOrderException();
    }
    return true;
  }
}
