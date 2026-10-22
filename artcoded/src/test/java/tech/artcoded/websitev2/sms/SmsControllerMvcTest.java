package tech.artcoded.websitev2.sms;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SmsControllerMvcTest {
    @Test void postJsonDeserializesSmsAndDelegatesToService() throws Exception {
        var service = mock(SmsService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SmsRestController(service)).build();
        mvc.perform(post("/api/sms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"+32123\",\"message\":\"Hi\"}"))
                .andExpect(status().isOk());
        verify(service).send(argThat(sms -> "+32123".equals(sms.getPhoneNumber()) && "Hi".equals(sms.getMessage())));
    }

    @Test void malformedJsonFailsBeforeCallingService() throws Exception {
        var service = mock(SmsService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SmsRestController(service)).build();
        mvc.perform(post("/api/sms").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
