package com.agentsystem.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * A failing record (e.g. Resend rejects the address) is retried twice, 1s apart,
 * then logged and skipped — without this, Spring Kafka's default handler retries
 * indefinitely and stalls the partition. OTP codes expire within minutes anyway,
 * so an endlessly-retried delivery would be useless.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 2));
    }
}
