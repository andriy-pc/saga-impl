package warehouseservice.controller;

import common.dto.ProductDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import warehouseservice.service.ProductService;

@RestController
@RequestMapping("/warehouse/products")
@RequiredArgsConstructor
public class ProductsController {

  private final ProductService productService;

  @GetMapping("/available")
  public List<ProductDTO> getAvailableItems() {
    return productService.getAvailableProducts();
  }

  @GetMapping("/{productId}")
  public ProductDTO getByProductId(@PathVariable Integer productId) {
    return productService.getProductById(productId);
  }
}
