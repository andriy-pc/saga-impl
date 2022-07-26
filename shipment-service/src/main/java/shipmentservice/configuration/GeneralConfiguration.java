package shipmentservice.configuration;

import java.util.Random;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeneralConfiguration {

  @Bean
  Random random() {
    return new Random();
  }
}
