package tech.artcoded.websitev2.pages.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.IFileUploadService;

/** The second phone number follows the first one: stored on the personal info and sent by the settings form. */
class PersonalInfoSecondPhoneTest {
    private PersonalInfoRepository repository;
    private PersonalInfoService service;

    @BeforeEach
    void setUp() {
        repository = mock(PersonalInfoRepository.class);
        service = new PersonalInfoService(repository, mock(NotificationService.class), mock(IFileUploadService.class));
        when(repository.save(any(PersonalInfo.class))).thenAnswer(a -> a.getArgument(0));
    }

    private void stored(PersonalInfo current) {
        when(repository.findAll()).thenReturn(List.of(current));
    }

    @Test
    void emptyByDefaultLikeTheMigration() {
        assertThat(PersonalInfo.builder().build().getOrganizationSecondPhoneNumber()).isEmpty();
    }

    @Test
    void savedWithTheFirstPhone() {
        stored(PersonalInfo.builder().id("me").build());

        var saved = service.save(PersonalInfo.builder().organizationPhoneNumber("+32 470 00 00 00")
                .organizationSecondPhoneNumber("+32 2 000 00 00").build(), null, null, null);

        assertThat(saved.getOrganizationPhoneNumber()).isEqualTo("+32 470 00 00 00");
        assertThat(saved.getOrganizationSecondPhoneNumber()).isEqualTo("+32 2 000 00 00");
    }

    @Test
    void canBeCleared() {
        stored(PersonalInfo.builder().id("me").organizationSecondPhoneNumber("+32 2 000 00 00").build());

        var saved = service.save(PersonalInfo.builder().organizationSecondPhoneNumber("").build(), null, null, null);

        assertThat(saved.getOrganizationSecondPhoneNumber()).isEmpty();
    }

    @Test
    void keptWhenTheClientDoesNotSendIt() {
        // the current frontend does not know the field yet: saving the settings must not wipe it
        stored(PersonalInfo.builder().id("me").organizationSecondPhoneNumber("+32 2 000 00 00").build());

        var saved = service.save(PersonalInfo.builder().organizationSecondPhoneNumber(null).build(), null, null, null);

        assertThat(saved.getOrganizationSecondPhoneNumber()).isEqualTo("+32 2 000 00 00");
    }

    private static MockMultipartHttpServletRequestBuilder settingsForm() {
        var request = multipart("/api/personal-info/submit");
        request.param("ceoFullName", "Nordine").param("note", "").param("organizationAddress", "street")
                .param("demoMode", "false").param("financeCharge", "1").param("organizationCity", "Brussels")
                .param("organizationName", "ARTCODED").param("organizationBankAccount", "BE00")
                .param("organizationBankBIC", "GEBABEBB").param("organizationEmailAddress", "a@b.c")
                .param("organizationPostCode", "1000").param("countryCode", "BE").param("maxDaysToPay", "30")
                .param("organizationPhoneNumber", "+32 470 00 00 00").param("accountants", "[]")
                .param("vatNumber", "BE0123456789");
        return request;
    }

    @Test
    void formFieldIsOptional() throws Exception {
        var mockService = mock(PersonalInfoService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PersonalInfoController(mockService)).build();
        var captor = ArgumentCaptor.forClass(PersonalInfo.class);

        // old frontend: no second phone sent
        mvc.perform(settingsForm()).andExpect(status().isOk());
        // new frontend
        mvc.perform(settingsForm().param("organizationSecondPhoneNumber", "+32 2 000 00 00"))
                .andExpect(status().isOk());

        verify(mockService, org.mockito.Mockito.times(2)).save(captor.capture(), any(), any(), any());
        assertThat(captor.getAllValues()).extracting(PersonalInfo::getOrganizationSecondPhoneNumber)
                .containsExactly(null, "+32 2 000 00 00");
        assertThat(captor.getAllValues()).extracting(PersonalInfo::getOrganizationPhoneNumber)
                .containsOnly("+32 470 00 00 00");
    }
}
