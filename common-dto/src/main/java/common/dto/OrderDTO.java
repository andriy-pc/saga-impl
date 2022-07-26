package common.dto;

import lombok.Data;

@Data
public class OrderDTO {

  private Integer userId;
  private Integer productId;
  private Integer orderedQty;
}
