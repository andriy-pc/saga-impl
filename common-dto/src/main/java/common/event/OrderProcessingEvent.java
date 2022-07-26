package common.event;

import common.dto.OrderDTO;
import common.enums.OrderStatus;
import common.enums.ProcessingStatus;
import java.util.UUID;
import lombok.Data;

@Data
public class OrderProcessingEvent {

  private UUID uuid = UUID.randomUUID();
  private OrderDTO orderDTO;

  private StringBuilder processingResultMessage = new StringBuilder();
  private OrderStatus orderStatus;
  private ProcessingStatus userProcessingStatus = ProcessingStatus.UNPROCESSED;
  private ProcessingStatus warehouseProcessingStatus = ProcessingStatus.UNPROCESSED;
  private ProcessingStatus shipmentProcessingStatus = ProcessingStatus.UNPROCESSED;

  public void addMessage(String message) {
    processingResultMessage.append(message);
    processingResultMessage.append("\n");
  }

  public boolean processed() {
    return !getOrderStatus().equals(OrderStatus.REQUESTED);
  }

  public boolean toProcessForUser() {
    return userProcessingStatus.equals(ProcessingStatus.UNPROCESSED);
  }

  public boolean toProcessForWarehouse() {
    return warehouseProcessingStatus.equals(ProcessingStatus.UNPROCESSED)
        && userProcessingStatus.equals(ProcessingStatus.SUCCESS);
  }

  public boolean toProcessForShipment() {
    return shipmentProcessingStatus.equals(ProcessingStatus.UNPROCESSED)
        && warehouseProcessingStatus.equals(ProcessingStatus.SUCCESS);
  }

  public String getProcessingResultMessage() {
    return processingResultMessage.toString();
  }
}
