package tech.artcoded.websitev2.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import tech.artcoded.websitev2.pages.mail.MailJobRepository;

class ActionServiceTest {
    private ProducerTemplate producerTemplate;
    private ActionResultRepository resultRepository;
    private MailJobRepository mailJobRepository;
    private ActionService service;

    record TestAction(String key, Optional<ActionRequest> defaultRequest) implements Action {
        @Override
        public ActionResult run(List<ActionParameter> parameters) {
            return actionResultBuilder(parameters).build();
        }

        @Override
        public ActionMetadata getMetadata() {
            return ActionMetadata.builder().key(key).title(key).defaultCronValue("0 0 3 * * *").build();
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Optional<ActionRequest> getDefaultActionRequest() {
            return defaultRequest;
        }
    }

    @BeforeEach
    void setUp() {
        producerTemplate = mock(ProducerTemplate.class);
        resultRepository = mock(ActionResultRepository.class);
        mailJobRepository = mock(MailJobRepository.class);
        var actions = List.<Action> of(new TestAction("USER_ACTION", Optional.empty()),
                new TestAction("INTERNAL_ACTION", Optional.of(ActionRequest.builder().actionKey("INTERNAL_ACTION").build())));
        service = new ActionService(producerTemplate, resultRepository, mock(CamelContext.class), mailJobRepository,
                actions);
        ReflectionTestUtils.setField(service, "adminEmail", "admin@example.com");
    }

    @Test
    void performingAUserActionSendsTheRequestToCamel() {
        var params = List.of(ActionParameter.builder().key("days").value("30").build());
        service.perform("USER_ACTION", params, true, false, true);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(producerTemplate).sendBody(eq(ActionService.ACTION_ENDPOINT), captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(ActionRequest.class, request -> {
            assertThat(request.getActionKey()).isEqualTo("USER_ACTION");
            assertThat(request.isSendMail()).isTrue();
            assertThat(request.isSendSms()).isFalse();
            assertThat(request.isPersistResult()).isTrue();
            assertThat(request.getParameters()).isEqualTo(params);
        });
        verify(mailJobRepository, never()).sendDelayedMail(any(), anyString(), anyString(), anyBoolean(), anyList(),
                any());
    }

    @Test
    void internalActionsCannotBeTriggeredManually() {
        service.perform("INTERNAL_ACTION", List.of(), false, false, false);
        verify(producerTemplate, never()).sendBody(anyString(), any());
        verify(mailJobRepository).sendDelayedMail(eq(List.of("admin@example.com")), contains("internal action"),
                anyString(), eq(false), eq(List.of()), any(LocalDateTime.class));
    }

    @Test
    void unknownActionsAreReportedToTheAdmin() {
        service.perform("DOES_NOT_EXIST", List.of(), false, false, false);
        verify(producerTemplate, never()).sendBody(anyString(), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> to = ArgumentCaptor.forClass(Collection.class);
        verify(mailJobRepository).sendDelayedMail(to.capture(), contains("doesn't seem to exist"),
                contains("DOES_NOT_EXIST"), eq(false), eq(List.of()), any(LocalDateTime.class));
        assertThat(to.getValue()).containsExactly("admin@example.com");
    }

    @Test
    void onlyUserActionsAreListed() {
        assertThat(service.getAllowedActions()).extracting(ActionMetadata::getKey).containsExactly("USER_ACTION");
    }

    @Test
    void resultsAreReadFromTheRepository() {
        var page = new PageImpl<>(List.of(ActionResult.builder().actionKey("USER_ACTION").build()));
        when(resultRepository.findByActionKeyOrderByCreatedDateDesc("USER_ACTION", PageRequest.of(0, 5)))
                .thenReturn(page);
        when(resultRepository.count()).thenReturn(7L);
        assertThat(service.findActionResults("USER_ACTION", PageRequest.of(0, 5))).isSameAs(page);
        assertThat(service.count()).isEqualTo(7);
        var date = new Date();
        service.deleteByFinishedDateBefore(date);
        verify(resultRepository).deleteByFinishedDateBefore(date);
    }
}
