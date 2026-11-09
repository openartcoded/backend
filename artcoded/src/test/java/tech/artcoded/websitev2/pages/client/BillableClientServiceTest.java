package tech.artcoded.websitev2.pages.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.event.IEvent;
import tech.artcoded.event.v1.client.BillableClientCreatedOrUpdated;
import tech.artcoded.event.v1.client.BillableClientDeleted;
import tech.artcoded.event.v1.client.BillableClientDocumentAddedOrUpdated;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.IFileUploadService;

class BillableClientServiceTest {
    private BillableClientRepository repository;
    private IFileUploadService uploadService;
    private NotificationService notificationService;
    private ExposedEventService eventService;
    private BillableClientService service;

    @BeforeEach
    void setUp() {
        repository = mock(BillableClientRepository.class);
        uploadService = mock(IFileUploadService.class);
        notificationService = mock(NotificationService.class);
        eventService = mock(ExposedEventService.class);
        service = new BillableClientService(repository, uploadService, notificationService, eventService);
        when(repository.save(any(BillableClient.class))).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void saveNormalizesRatesAndMirrorsTheDefaultOneInLegacyFields() {
        var rates = new ArrayList<ClientRate>();
        rates.add(ClientRate.builder().id(null).label("  Night ").rate(new BigDecimal("80")).rateType(RateType.HOURS)
                .build());
        rates.add(ClientRate.builder().rate(null).rateType(RateType.DAYS).build());
        var client = BillableClient.builder().id("c1").name("ACME").contractStatus(ContractStatus.ONGOING)
                .rate(new BigDecimal("1")).rateType(RateType.DAYS).rates(rates).build();

        var saved = service.save(client);

        assertThat(saved.getRates()).hasSize(1);
        assertThat(saved.getRates().getFirst().getId()).isNotBlank();
        assertThat(saved.getRates().getFirst().getLabel()).isEqualTo("Night");
        assertThat(saved.getRate()).isEqualByComparingTo("80");
        assertThat(saved.getRateType()).isEqualTo(RateType.HOURS);
    }

    @Test
    void saveKeepsDocumentsAlreadyLinkedToTheClient() {
        var stored = BillableClient.builder().id("c1").name("ACME").documentIds(List.of("doc-1")).build();
        when(repository.findById("c1")).thenReturn(Optional.of(stored));
        var received = BillableClient.builder().id("c1").name("ACME renamed").contractStatus(ContractStatus.ONGOING)
                .documentIds(List.of()).build();

        var saved = service.save(received);

        // the stored entity is returned (it used to return the payload received, losing the documents)
        assertThat(saved.getName()).isEqualTo("ACME renamed");
        assertThat(saved.getDocumentIds()).containsExactly("doc-1");
    }

    @Test
    void saveWithoutContractStatusDoesNotFail() {
        // unknown id: a new client is created with a generated id
        var saved = service.save(BillableClient.builder().id("c2").name("New").build());
        assertThat(saved.getName()).isEqualTo("New");
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(BillableClientCreatedOrUpdated.class, e -> {
            assertThat(e.getClientId()).isEqualTo(saved.getId());
            assertThat(e.getContractStatus()).isNull();
        });
    }

    @Test
    void deleteSendsEventOnlyWhenClientExists() {
        var client = BillableClient.builder().id("c1").build();
        when(repository.findById("c1")).thenReturn(Optional.of(client));
        service.delete("c1");
        service.delete("unknown");
        verify(repository).delete(client);
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BillableClientDeleted.class);
    }

    @Test
    void uploadLinksDocumentToClient() {
        when(repository.findById("c1"))
                .thenReturn(Optional.of(BillableClient.builder().id("c1").name("ACME").documentIds(null).build()));
        when(uploadService.upload(any(MultipartFile.class), eq("c1"), anyBoolean())).thenReturn("upload-1");

        service.upload(new MockMultipartFile("doc", "contract.pdf", "application/pdf", new byte[] { 1 }), "c1");

        var captor = ArgumentCaptor.forClass(BillableClient.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDocumentIds()).containsExactly("upload-1");
        verify(notificationService).sendEvent(startsWith("Document added"), eq("BILLABLE_CLIENT_UPLOAD_ADDED"),
                eq("c1"));
        var event = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(BillableClientDocumentAddedOrUpdated.class);
    }

    @Test
    void uploadForUnknownClientNotifiesAnError() {
        service.upload(new MockMultipartFile("doc", new byte[] { 1 }), "unknown");
        verify(uploadService, never()).upload(any(MultipartFile.class), anyString(), anyBoolean());
        verify(notificationService).sendEvent(startsWith("Could not upload"), eq("BILLABLE_CLIENT_ERROR"),
                eq("unknown"));
    }

    @Test
    void deleteUploadOnlyDeletesDocumentsOfTheClient() {
        when(repository.findById("c1")).thenReturn(
                Optional.of(BillableClient.builder().id("c1").name("ACME").documentIds(List.of("a", "b")).build()));

        service.deleteUpload("c1", "not-mine");
        verify(uploadService, never()).delete(anyString());
        verify(notificationService).sendEvent(startsWith("Could not delete"), eq("BILLABLE_CLIENT_ERROR"), eq("c1"));

        service.deleteUpload("c1", "a");
        verify(uploadService).delete("a");
        var captor = ArgumentCaptor.forClass(BillableClient.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDocumentIds()).containsExactly("b");
    }

    @Test
    void deleteUploadWithoutDocumentsDoesNotFail() {
        when(repository.findById("c1"))
                .thenReturn(Optional.of(BillableClient.builder().id("c1").documentIds(null).build()));
        service.deleteUpload("c1", "a");
        verify(uploadService, never()).delete(anyString());
    }

    @Test
    void findOneByCompanyNumberIgnoresCountryPrefixAndFormatting() {
        when(repository.findByOrderByContractStatusDesc())
                .thenReturn(List.of(BillableClient.builder().name("other").vatNumber("BE0999999999").build(),
                        BillableClient.builder().name("ACME").vatNumber("BE 0123.456.749").build()));
        assertThat(service.findOneByCompanyNumber("0123456749")).map(BillableClient::getName).contains("ACME");
        assertThat(service.findOneByCompanyNumber("0000000000")).isEmpty();
    }

    @Test
    void oldDocumentIdIsReplaced() {
        when(repository.findById("c1"))
                .thenReturn(Optional.of(BillableClient.builder().id("c1").documentIds(List.of("old", "x")).build()));
        service.updateOldId("c1", "old", "new");
        var captor = ArgumentCaptor.forClass(BillableClient.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDocumentIds()).containsExactlyInAnyOrder("x", "new");
    }

    @Test
    void correlationLabels() {
        var client = BillableClient.builder().id("c1").name("ACME").build();
        when(repository.findById("c1")).thenReturn(Optional.of(client));
        when(repository.findAllById(List.of("c1"))).thenReturn(List.of(client));
        assertThat(service.getCorrelationLabel("c1")).isEqualTo("Client 'ACME' ");
        assertThat(service.getCorrelationLabels(List.of("c1"))).containsEntry("c1", "Client 'ACME' ");
    }
}
