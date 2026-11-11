package until.the.eternity.das.experience;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class ExperienceKafkaConfiguration {
    @Bean(destroyMethod = "close")
    public ExperienceDeadLetters experienceDeadLetters(KafkaProperties properties) {
        return new ExperienceDeadLetters(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
            experienceKafkaListenerContainerFactory(
                    ConsumerFactory<String, String> consumers, ExperienceDeadLetters letters) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        letters.template,
                        (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        DefaultErrorHandler errors = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 5));
        errors.addNotRetryableExceptions(IllegalArgumentException.class);
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        Map<String, Object> consumerProperties =
                new HashMap<>(consumers.getConfigurationProperties());
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerProperties));
        factory.setCommonErrorHandler(errors);
        factory.getContainerProperties()
                .setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    public static final class ExperienceDeadLetters implements AutoCloseable {
        private final DefaultKafkaProducerFactory<String, String> producers;
        private final KafkaTemplate<String, String> template;

        ExperienceDeadLetters(KafkaProperties properties) {
            Map<String, Object> producerProperties =
                    new HashMap<>(properties.buildProducerProperties());
            producerProperties.put(
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producerProperties.put(
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
            producers = new DefaultKafkaProducerFactory<>(producerProperties);
            template = new KafkaTemplate<>(producers);
        }

        @Override
        public void close() {
            template.destroy();
            producers.destroy();
        }
    }
}
