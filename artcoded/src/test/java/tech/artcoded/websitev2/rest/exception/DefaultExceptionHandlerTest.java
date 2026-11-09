package tech.artcoded.websitev2.rest.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import tech.artcoded.websitev2.pages.mail.MailJobRepository;

/** Real Spring MVC dispatching (standalone MockMvc) through the global exception handler. */
class DefaultExceptionHandlerTest {
    private static final String ADMIN_EMAIL = "admin@artcoded.test";

    private MailJobRepository mailJobRepository;
    private DefaultExceptionHandler handler;
    private MockMvc mvc;

    @RestController
    static class FailingController {
        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom happened");
        }

        @GetMapping("/missing")
        String missing() throws NoResourceFoundException {
            // Spring Framework 7: (method, request path, resource path)
            throw new NoResourceFoundException(HttpMethod.GET, "/missing", "missing");
        }
    }

    @BeforeEach
    void setUp() {
        mailJobRepository = mock(MailJobRepository.class);
        handler = new DefaultExceptionHandler(mailJobRepository);
        ReflectionTestUtils.setField(handler, "adminEmail", ADMIN_EMAIL);
        mvc = MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(handler).build();
    }

    @Test
    void regularUsersOnlyGetAGenericMessage() throws Exception {
        mvc.perform(get("/boom")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Server error")).andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void adminsGetTheStackTrace() throws Exception {
        mvc.perform(get("/boom").with(request -> {
            request.addUserRole("ADMIN");
            return request;
        })).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.stackTrace").value(containsString("boom happened")));
    }

    @Test
    void theAdminIsWarnedByMail() throws Exception {
        mvc.perform(get("/boom")).andExpect(status().isBadRequest());

        // sent from a virtual thread
        verify(mailJobRepository, timeout(5_000)).sendDelayedMail(eq(List.of(ADMIN_EMAIL)), eq("Artcoded error"),
                contains("boom happened"), eq(false), eq(List.of()), any());
    }

    @Test
    void aMailingFailureDoesNotChangeTheResponse() throws Exception {
        doThrow(new RuntimeException("smtp down")).when(mailJobRepository)
                .sendDelayedMail(anyList(), anyString(), anyString(), anyBoolean(), anyList(), any());

        mvc.perform(get("/boom")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Server error"));
    }

    @Test
    void missingResourcesAreA404WithoutMail() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound());

        verify(mailJobRepository, after(300).never()).sendDelayedMail(anyList(), anyString(), anyString(),
                anyBoolean(), anyList(), any());
    }

    @Test
    void clientDisconnectionsAreIgnored() {
        var request = new ServletWebRequest(new MockHttpServletRequest());
        var disconnected = new UncheckedIOException(new IOException("Broken pipe"));

        assertThat(handler.runtimeException(request, disconnected)).isNull();
        verify(mailJobRepository, after(300).never()).sendDelayedMail(anyList(), anyString(), anyString(),
                anyBoolean(), anyList(), any());
    }
}
