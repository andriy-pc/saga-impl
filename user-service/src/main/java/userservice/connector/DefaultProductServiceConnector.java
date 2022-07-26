package userservice.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.dto.ProductDTO;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DefaultProductServiceConnector implements ProductServiceConnector {

  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;

  @Value("${config.warehouse.url}")
  private String warehouseURL;

  @Override
  public ProductDTO getProductById(Integer productId) {

    Request request =
        new Request.Builder()
            .get()
            .url("http://" + warehouseURL + "/warehouse/products/" + productId)
            .build();

    try (Response response = okHttpClient.newCall(request).execute();
        ResponseBody responseBody = response.body()) {
      if (Objects.nonNull(responseBody)) {
        String serializedProduct = responseBody.string();
        return objectMapper.readValue(serializedProduct, ProductDTO.class);
      }
    } catch (IOException e) {
      log.error("Exception occurred during getting product from warehouse service. Exception: ", e);
    }
    return null;
  }
}
