package com.jing.monitor.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * RabbitMQ exchange, queue, binding, and converter configuration.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public Declarables alertQueueTopology(
            @Value("${app.rabbitmq.exchange}") String alertExchangeName,
            @Value("${app.rabbitmq.routing-key}") String alertRoutingKey,
            @Value("${app.rabbitmq.queue}") String alertQueueName,
            @Value("${app.rabbitmq.dlx-exchange}") String deadLetterExchangeName,
            @Value("${app.rabbitmq.dlq-routing-key}") String deadLetterRoutingKey,
            @Value("${app.rabbitmq.dlq}") String deadLetterQueueName
    ) {
        // Main exchange receives all alert events published by the scheduler or admin tools.
        DirectExchange alertExchange = new DirectExchange(alertExchangeName, true, false);

        // Dead-letter exchange receives only rejected alert messages.
        DirectExchange deadLetterExchange = new DirectExchange(deadLetterExchangeName, true, false);

        // Main alert queue stores mail delivery jobs and forwards rejected messages into the DLX.
        Queue alertQueue = new Queue(
                alertQueueName,
                true,
                false,
                false,
                Map.of(
                        // Route rejected messages into the dead-letter exchange.
                        "x-dead-letter-exchange", deadLetterExchangeName,
                        // Keep the dead-letter routing key explicit for easier inspection later.
                        "x-dead-letter-routing-key", deadLetterRoutingKey
                )
        );

        // Dead-letter queue stores only failed alert jobs for manual follow-up.
        Queue deadLetterQueue = new Queue(deadLetterQueueName, true);

        // Bind the main queue so normal alert events can be consumed by the mail worker.
        Binding alertBinding = BindingBuilder.bind(alertQueue).to(alertExchange).with(alertRoutingKey);

        // Bind the DLQ so rejected messages become visible to the dead-letter listener.
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(deadLetterRoutingKey);

        return new Declarables(alertExchange, deadLetterExchange, alertQueue, deadLetterQueue, alertBinding, deadLetterBinding);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        // Serialize AlertEvent as JSON so publisher and consumer share a stable payload shape.
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        // Use the shared JSON converter for every outgoing RabbitMQ message.
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        return rabbitTemplate;
    }
}
