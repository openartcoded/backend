package tech.artcoded.websitev2.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.artcoded.websitev2.utils.common.Constants.CORRELATION_ID;
import static tech.artcoded.websitev2.utils.common.Constants.NOTIFICATION_ENDPOINT;
import static tech.artcoded.websitev2.utils.common.Constants.NOTIFICATION_HEADER_TITLE;
import static tech.artcoded.websitev2.utils.common.Constants.NOTIFICATION_HEADER_TYPE;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {
    private NotificationRepository repository;
    private ProducerTemplate producerTemplate;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        producerTemplate = mock(ProducerTemplate.class);
        service = new NotificationService(repository, producerTemplate);
        when(repository.save(any(Notification.class))).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void eventIsPublishedWithItsHeaders() {
        service.sendEvent("Invoice ready", "NEW_INVOICE", "inv-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headers = ArgumentCaptor.forClass(Map.class);
        verify(producerTemplate).sendBodyAndHeaders(eq(NOTIFICATION_ENDPOINT), isNull(), headers.capture());
        assertThat(headers.getValue()).containsEntry(NOTIFICATION_HEADER_TITLE, "Invoice ready")
                .containsEntry(NOTIFICATION_HEADER_TYPE, "NEW_INVOICE").containsEntry(CORRELATION_ID, "inv-1");
    }

    @ParameterizedTest
    @CsvSource(value = { "NULL, type, id", "title, NULL, id", "title, type, NULL" }, nullValues = "NULL")
    void incompleteEventsAreNotPublished(String title, String type, String correlationId) {
        // Map.of would throw on null values: they must be filtered before
        service.sendEvent(title, type, correlationId);
        verify(producerTemplate, never()).sendBodyAndHeaders(anyString(), any(), anyMap());
    }

    @Test
    void notifyStoresANewNotification() {
        var notification = service.notify("title", "TYPE", "corr");
        assertThat(notification.getTitle()).isEqualTo("title");
        assertThat(notification.getType()).isEqualTo("TYPE");
        assertThat(notification.getCorrelationId()).isEqualTo("corr");
        assertThat(notification.isSeen()).isFalse();
    }

    @Test
    void markAsSeen() {
        when(repository.findById("n1")).thenReturn(Optional.of(Notification.builder().id("n1").seen(false).build()));
        service.update("n1", true);
        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isSeen()).isTrue();

        service.update("unknown", true);
        verify(repository).save(any()); // still once
    }

    @Test
    void deletion() {
        service.delete("n1");
        verify(repository).deleteById("n1");
        service.deleteAll();
        verify(repository).deleteAll();
    }
}
