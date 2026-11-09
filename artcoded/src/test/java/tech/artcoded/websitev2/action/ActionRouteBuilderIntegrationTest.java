package tech.artcoded.websitev2.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.builder.AdviceWith;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import freemarker.template.Configuration;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.sms.Sms;
import tech.artcoded.websitev2.sms.SmsService;

/**
 * Runs the real Camel route and the real FreeMarker templates of the action engine. Only the JMS consumer is replaced
 * by a direct endpoint.
 */
class ActionRouteBuilderIntegrationTest {
    private DefaultCamelContext camel;
    private PersonalInfoService personalInfoService;
    private MailJobRepository mailJobRepository;
    private SmsService smsService;
    private ActionResultRepository resultRepository;
    private final List<List<ActionParameter>> runs = new CopyOnWriteArrayList<>();
    private StatusType nextStatus = StatusType.SUCCESS;
    private List<String> nextMessages = List.of("deleted 3 documents");
    private boolean refuseToRun;

    class RecordingAction implements Action {
        @Override
        public ActionResult run(List<ActionParameter> parameters) {
            runs.add(parameters);
            return actionResultBuilder(parameters).status(nextStatus).messages(new ArrayList<>(nextMessages))
                    .finishedDate(new Date()).build();
        }

        @Override
        public boolean shouldNotRun(List<ActionParameter> parameters) {
            return refuseToRun;
        }

        @Override
        public ActionMetadata getMetadata() {
            return ActionMetadata.builder().key(getKey()).build();
        }

        @Override
        public String getKey() {
            return "CLEANUP";
        }
    }

    @BeforeEach
    void startCamel() throws Exception {
        personalInfoService = mock(PersonalInfoService.class);
        mailJobRepository = mock(MailJobRepository.class);
        smsService = mock(SmsService.class);
        resultRepository = mock(ActionResultRepository.class);
        when(personalInfoService.getOptional()).thenReturn(Optional.of(PersonalInfo.builder()
                .organizationEmailAddress("me@example.com").organizationPhoneNumber("+32470000000").build()));

        var freemarker = new Configuration(Configuration.VERSION_2_3_32);
        freemarker.setClassForTemplateLoading(getClass(), "/templates/");

        camel = new DefaultCamelContext();
        camel.addRoutes(new ActionRouteBuilder(List.of(new RecordingAction()), personalInfoService, mailJobRepository,
                smsService, freemarker, resultRepository));
        AdviceWith.adviceWith(camel, "actionRoute", route -> route.replaceFromWith("direct:action"));
        camel.start();
    }

    @AfterEach
    void stopCamel() {
        camel.stop();
    }

    private void send(ActionRequest request) {
        // same JSON text as ActionService sends through JMS
        camel.createProducerTemplate().sendBody("direct:action", request.toJson());
    }

    private static ActionRequest request(boolean mail, boolean sms, boolean persist) {
        return ActionRequest.builder().actionKey("CLEANUP").sendMail(mail).sendSms(sms).persistResult(persist)
                .parameters(List.of(ActionParameter.builder().key("days").value("30").build())).build();
    }

    @Test
    void actionRunsAndResultIsMailedWithTheRenderedTemplate() {
        send(request(true, false, false));

        assertThat(runs).hasSize(1);
        assertThat(runs.getFirst()).extracting(ActionParameter::getKey).containsExactly("days");
        var body = ArgumentCaptor.forClass(String.class);
        verify(mailJobRepository).sendDelayedMail(eq(List.of("me@example.com")), eq("Batch Action: CLEANUP"),
                body.capture(), eq(false), eq(List.of()), any(LocalDateTime.class));
        assertThat(body.getValue()).contains("Status: SUCCESS").contains("<li>deleted 3 documents</li>");
        verify(smsService, never()).send(any());
        verify(resultRepository, never()).save(any());
    }

    @Test
    void failuresAreAlwaysMailed() {
        nextStatus = StatusType.FAILURE;
        send(request(false, false, false));
        verify(mailJobRepository).sendDelayedMail(any(), eq("Batch Action: CLEANUP"), anyString(), anyBoolean(),
                anyList(), any());
    }

    @Test
    void smsIsSentWithTheRenderedSmsTemplate() {
        send(request(false, true, false));
        var sms = ArgumentCaptor.forClass(Sms.class);
        verify(smsService).send(sms.capture());
        assertThat(sms.getValue().getPhoneNumber()).isEqualTo("+32470000000");
        assertThat(sms.getValue().getMessage()).contains("Action: CLEANUP").contains("deleted 3 documents");
        verify(mailJobRepository, never()).sendDelayedMail(any(), anyString(), anyString(), anyBoolean(), anyList(),
                any());
    }

    @Test
    void resultIsPersistedOnlyWhenRequestedAndNotEmpty() {
        send(request(false, false, true));
        verify(resultRepository).save(any(ActionResult.class));

        nextMessages = List.of();
        send(request(false, false, true));
        verify(resultRepository).save(any(ActionResult.class)); // still once
    }

    @Test
    void actionThatShouldNotRunIsSkipped() {
        refuseToRun = true;
        send(request(true, true, true));
        assertThat(runs).isEmpty();
        verify(mailJobRepository, never()).sendDelayedMail(any(), anyString(), anyString(), anyBoolean(), anyList(),
                any());
    }

    @Test
    void unknownActionIsIgnored() {
        send(ActionRequest.builder().actionKey("UNKNOWN").build());
        assertThat(runs).isEmpty();
    }

    @Test
    void withoutPersonalEmailNoMailIsSent() {
        when(personalInfoService.getOptional()).thenReturn(Optional.empty());
        send(request(true, true, false));
        assertThat(runs).hasSize(1);
        verify(mailJobRepository, never()).sendDelayedMail(any(), anyString(), anyString(), anyBoolean(), anyList(),
                any());
        verify(smsService, never()).send(any());
    }
}
