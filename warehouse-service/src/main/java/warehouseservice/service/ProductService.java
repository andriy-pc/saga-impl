package warehouseservice.service;

import common.dto.ProductDTO;
import java.util.List;

public interface ProductService {

  List<ProductDTO> getAvailableProducts();

  ProductDTO getProductById(final Integer productId);
}
