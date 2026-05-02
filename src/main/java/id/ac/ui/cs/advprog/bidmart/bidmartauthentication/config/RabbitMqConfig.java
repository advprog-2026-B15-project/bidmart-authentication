package id.ac.ui.cs.advprog.bidmart.bidmartauthentication.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true")
public class RabbitMqConfig {

    public static final String EXCHANGE = "bidmart.auth.events";

    @Bean
    public TopicExchange authEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }
}
