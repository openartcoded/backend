package tech.artcoded.websitev2.sms;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmsSpringContextTest {
    @Test
    void springInjectsConfiguredQueueAndCamelProducer() throws Exception {
        var producer = mock(ProducerTemplate.class);
        new ApplicationContextRunner().withPropertyValues("application.sms.smsPublish=direct:configured-sms")
                .withBean(ProducerTemplate.class, () -> producer).withBean(SmsService.class).run(context -> {
                    assertThat(context).hasSingleBean(SmsService.class);
                    context.getBean(SmsService.class)
                            .send(Sms.builder().phoneNumber("123 456").message("hello").build());
                    verify(producer).sendBody(eq("direct:configured-sms"),
                            argThat((Object body) -> body instanceof String && ((String) body).contains("123456")));
                });
    }
}
