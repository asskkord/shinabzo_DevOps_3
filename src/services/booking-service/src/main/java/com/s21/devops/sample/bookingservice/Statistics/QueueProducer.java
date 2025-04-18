package com.s21.devops.sample.bookingservice.Statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s21.devops.sample.bookingservice.Communication.BookingStatisticsMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;


@Component
public class QueueProducer {

    private final Counter messagesSent;

    @Autowired
    public QueueProducer(MeterRegistry meterRegistry) {
        this.messagesSent = meterRegistry.counter("rabbitmq_messages_sent");
    }

    @Value("${fanout.exchange}")
    private String fanoutExchange;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    public void putStatistics(BookingStatisticsMessage bookingStatisticsMessage) throws JsonProcessingException {
        messagesSent.increment();
        System.out.println("Sending message...");
        rabbitTemplate.setExchange(fanoutExchange);
        rabbitTemplate.convertAndSend(objectMapper.writeValueAsString(bookingStatisticsMessage));
        System.out.println("Message was sent successfully!");
    }
}
