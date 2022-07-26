package userservice.connector;

import common.dto.ProductDTO;

public interface ProductServiceConnector {

  ProductDTO getProductById(Integer productId);
}
