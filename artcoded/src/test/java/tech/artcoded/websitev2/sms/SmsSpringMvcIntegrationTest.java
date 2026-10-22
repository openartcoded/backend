package tech.artcoded.websitev2.sms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises real MVC registration, Jackson conversion and error handling. */
@SpringBootTest(classes = SmsSpringMvcIntegrationTest.App.class)
@AutoConfigureMockMvc(addFilters = false)
class SmsSpringMvcIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SmsService smsService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(SmsRestController.class)
    static class App {
        @Bean SmsService smsService() { return mock(SmsService.class); }
    }

    @Test void jacksonDeserializesJsonAndMvcDispatchesToBean() throws Exception {
        mvc.perform(post("/api/sms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"+32 456\",\"message\":\"hello\"}"))
                .andExpect(status().isOk());
        verify(smsService).send(argThat(s -> "+32 456".equals(s.getPhoneNumber())
                && "hello".equals(s.getMessage())));
    }

    @Test void invalidJsonDoesNotReachService() throws Exception {
        clearInvocations(smsService);
        mvc.perform(post("/api/sms").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(smsService);
    }

    @Test void unsupportedContentTypeDoesNotReachService() throws Exception {
        clearInvocations(smsService);
        mvc.perform(post("/api/sms").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(smsService);
    }
}
