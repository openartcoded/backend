package tech.artcoded.websitev2.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsServiceTest {
    @Test void normalizesPhoneNumberBeforePublishingToConfiguredCamelEndpoint() throws Exception {
        ProducerTemplate producer = mock(ProducerTemplate.class);
        SmsService service = new SmsService(producer);
        ReflectionTestUtils.setField(service, "queue", "direct:test-sms");
        var original = Sms.builder().phoneNumber("+32 (0) 123 / 45.67").message("Test message").build();
        service.send(original);
        var json = ArgumentCaptor.forClass(Object.class);
        verify(producer).sendBody(eq("direct:test-sms"), json.capture());
        var sent = new ObjectMapper().readTree((String) json.getValue());
        assertThat(sent.get("phoneNumber").asText()).isEqualTo(original.getCleanPhoneNumber());
        assertThat(sent.get("message").asText()).isEqualTo("Test message");
        assertThat(original.getPhoneNumber()).isEqualTo("+32 (0) 123 / 45.67");
    }
}
