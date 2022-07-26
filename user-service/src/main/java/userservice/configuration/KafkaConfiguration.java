package userservice.configuration;

import common.event.OrderProcessingEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfiguration {

  @Value("${config.kafka.url}")
  private String bootstrapServerURL;

  @Bean
  public ProducerFactory<String, OrderProcessingEvent> producerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServerURL);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, OrderProcessingEvent> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  ConsumerFactory<String, OrderProcessingEvent> consumerFactory() {
    Map<String, Object> configProps = consumerFactoryProperties();

    DefaultKafkaConsumerFactory<String, OrderProcessingEvent> consumerFactory =
        new DefaultKafkaConsumerFactory<>(
            configProps,
            new JsonDeserializer<>(String.class).forKeys().ignoreTypeHeaders(),
            new JsonDeserializer<>(OrderProcessingEvent.class).ignoreTypeHeaders());
    return consumerFactory;
  }

  private Map<String, Object> consumerFactoryProperties() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServerURL);
    configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    return configProps;
  }

  @Bean
  public KafkaListenerContainerFactory<
          ConcurrentMessageListenerContainer<String, OrderProcessingEvent>>
      kafkaListenerContainerFactory() {

    ConcurrentKafkaListenerContainerFactory<String, OrderProcessingEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());

    return factory;
  }
}
