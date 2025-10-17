package tech.artcoded.websitev2.sms;

import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

@Service
public class SmsService {
    @Value("${application.sms.smsPublish}")
    private String queue;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ProducerTemplate producerTemplate;

    public SmsService(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @Async
    @SneakyThrows
    public void send(Sms sms) {
        this.producerTemplate.sendBody(queue,
                MAPPER.writeValueAsString(sms.toBuilder().phoneNumber(sms.getCleanPhoneNumber()).build()));
    }

}
