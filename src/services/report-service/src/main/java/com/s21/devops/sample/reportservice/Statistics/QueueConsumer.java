package com.s21.devops.sample.reportservice.Statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s21.devops.sample.reportservice.Communication.BookingStatisticsMessage;
import com.s21.devops.sample.reportservice.Service.BookingStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;


@Component
public class QueueConsumer {

    private final Counter messagesProcessed;

    @Autowired
    private BookingStatsService bookingStatsService;

    @Autowired
    public QueueConsumer(MeterRegistry meterRegistry) {
        this.messagesProcessed = meterRegistry.counter("rabbitmq_messages_processed");
    }

    private ObjectMapper objectMapper = new ObjectMapper();

    public void receiveMessage(String message) throws JsonProcessingException {
        System.out.println("Received !!! (String) " + message);
        processMessage(message);
    }

    public void receiveMessage(byte[] message) throws JsonProcessingException {
        String strMessage = new String(message);
        System.out.println("Received !!! (No String) " + strMessage);
        processMessage(strMessage);
    }

    private void processMessage(String message) throws JsonProcessingException {
        messagesProcessed.increment();
        BookingStatisticsMessage bsm = objectMapper.readValue(message, BookingStatisticsMessage.class);
        bookingStatsService.postBookingStatsMessage(bsm);
    }
}