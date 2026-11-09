package tech.artcoded.websitev2.pages.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.event.IEvent;
import tech.artcoded.event.v1.timesheet.TimesheetDeleted;
import tech.artcoded.event.v1.timesheet.TimesheetReleased;
import tech.artcoded.event.v1.timesheet.TimesheetReopened;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.client.ClientRate;
import tech.artcoded.websitev2.pages.client.ContractStatus;
import tech.artcoded.websitev2.pages.invoice.BillTo;
import tech.artcoded.websitev2.pages.invoice.InvoiceFreemarkerTemplate;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.DateHelper;

class TimesheetServiceTest {
    private TimesheetRepository repository;
    private TimesheetToPdfService pdfService;
    private InvoiceService invoiceService;
    private ExposedEventService eventService;
    private IFileUploadService uploadService;
    private NotificationService notificationService;
    private BillableClientService clientService;
    private TimesheetService service;

    private static final ClientRate STANDARD = ClientRate.builder().id("std").rate(new BigDecimal("50"))
            .rateType(RateType.HOURS).build();
    private static final ClientRate SUPPORT = ClientRate.builder().id("sup").label("Support")
            .rate(new BigDecimal("100")).rateType(RateType.HOURS).build();

    @BeforeEach
    void setUp() {
        repository = mock(TimesheetRepository.class);
        pdfService = mock(TimesheetToPdfService.class);
        invoiceService = mock(InvoiceService.class);
        eventService = mock(ExposedEventService.class);
        uploadService = mock(IFileUploadService.class);
        notificationService = mock(NotificationService.class);
        clientService = mock(BillableClientService.class);
        service = new TimesheetService(repository, pdfService, invoiceService, eventService, uploadService,
                notificationService, clientService);
        when(repository.save(any(Timesheet.class))).thenAnswer(a -> a.getArgument(0));
    }

    private static BillableClient client() {
        return BillableClient.builder().id("client-1").name("ACME").projectName("Project X").nature("Consulting")
                .vatNumber("BE0123456749").address("Main street 1").city("Brussels").emailAddress("acme@example.com")
                .taxRate(new BigDecimal("21")).maxDaysToPay(30).rates(new ArrayList<>(List.of(STANDARD, SUPPORT)))
                .build();
    }

    private static TimesheetPeriod period(String id, int day, String rateId) {
        var d = LocalDate.of(2026, 10, day);
        return TimesheetPeriod.builder().id(id).date(DateHelper.toDate(d)).periodType(PeriodType.WORKING_DAY)
                .rateId(rateId).morningStartTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 9, 0)))
                .morningEndTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 12, 0)))
                .afternoonStartTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 13, 0)))
                .afternoonEndTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 17, 0))).build();
    }

    private static Timesheet timesheet(TimesheetPeriod... periods) {
        return Timesheet.builder().id("ts-1").name("10/2026").yearMonth(YearMonth.of(2026, 10)).clientId("client-1")
                .clientName("ACME").periods(new ArrayList<>(List.of(periods)))
                .settings(TimesheetSettings.builder().build()).build();
    }

    private IEvent lastEvent() {
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void closedTimesheetCannotBeUpdated() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet().toBuilder().closed(true).build()));
        assertThatThrownBy(() -> service.saveOrUpdateTimesheet(timesheet())).hasMessageContaining("closed");
        verify(repository, never()).save(any());
    }

    @Test
    void updateOnlyChangesNameAndPeriods() {
        var stored = timesheet().toBuilder().clientId("client-1").uploadId(null).build();
        when(repository.findById("ts-1")).thenReturn(Optional.of(stored));
        var received = timesheet(period("p1", 1, "std")).toBuilder().name("renamed").clientId("hacked")
                .invoiceId("hacked").build();

        var saved = service.saveOrUpdateTimesheet(received);

        assertThat(saved.getName()).isEqualTo("renamed");
        assertThat(saved.getPeriods()).hasSize(1);
        assertThat(saved.getClientId()).isEqualTo("client-1");
        assertThat(saved.getInvoiceId()).isNull();
    }

    @Test
    void savingAPeriodReplacesItAndKeepsPeriodsSortedByDate() {
        var ts = timesheet(period("p5", 5, "std"), period("p1", 1, "std"));
        when(repository.findById("ts-1")).thenReturn(Optional.of(ts));

        var updated = period("p5", 5, "sup").toBuilder().shortDescription("on call").build();
        var newOne = period("p3", 3, null);
        service.saveOrUpdateTimesheetPeriod("ts-1", updated);
        var captor = ArgumentCaptor.forClass(Timesheet.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPeriods()).extracting(TimesheetPeriod::getId).containsExactly("p1", "p5");
        assertThat(captor.getValue().getPeriods().get(1).getRateId()).isEqualTo("sup");
        assertThat(captor.getValue().getPeriods().get(1).getShortDescription()).isEqualTo("on call");

        when(repository.findById("ts-1")).thenReturn(Optional.of(captor.getValue()));
        var returned = service.saveOrUpdateTimesheetPeriod("ts-1", newOne);
        assertThat(returned.getId()).isEqualTo("p3");
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getPeriods()).extracting(TimesheetPeriod::getId).containsExactly("p1", "p3", "p5");
    }

    @Test
    void savingAPeriodOnUnknownOrClosedTimesheetFails() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.saveOrUpdateTimesheetPeriod("missing", period("p", 1, null)))
                .hasMessageContaining("not found");
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet().toBuilder().closed(true).build()));
        assertThatThrownBy(() -> service.saveOrUpdateTimesheetPeriod("ts-1", period("p", 1, null)))
                .hasMessageContaining("closed");
    }

    @Test
    void periodRateCanChangeOnClosedTimesheetWithoutInvoice() {
        when(repository.findById("ts-1"))
                .thenReturn(Optional.of(timesheet(period("p1", 1, "std")).toBuilder().closed(true).build()));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));

        var period = service.updatePeriodRate("ts-1", "p1", "sup");

        assertThat(period.getRateId()).isEqualTo("sup");
        verify(repository).save(any(Timesheet.class));
    }

    @Test
    void blankRateMeansDefaultRate() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet(period("p1", 1, "sup"))));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        assertThat(service.updatePeriodRate("ts-1", "p1", "  ").getRateId()).isNull();
    }

    @Test
    void periodRateCannotChangeOnceInvoiced() {
        when(repository.findById("ts-1"))
                .thenReturn(Optional.of(timesheet(period("p1", 1, "std")).toBuilder().invoiceId("inv").build()));
        assertThatThrownBy(() -> service.updatePeriodRate("ts-1", "p1", "sup")).hasMessageContaining("invoice");
        verify(repository, never()).save(any());
    }

    @Test
    void periodRateMustBelongToTheClient() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet(period("p1", 1, "std"))));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        assertThatThrownBy(() -> service.updatePeriodRate("ts-1", "p1", "other-client-rate"))
                .hasMessageContaining("does not belong");
        verify(repository, never()).save(any());
    }

    @Test
    void legacyClientWithoutExplicitRatesAcceptsAnyRate() {
        var legacy = BillableClient.builder().name("legacy").rate(BigDecimal.TEN).rateType(RateType.HOURS)
                .rates(List.of()).build();
        TimesheetService.checkRateBelongsToClient(legacy, "whatever");
    }

    @Test
    void unknownPeriodIsRejected() {
        assertThatThrownBy(() -> TimesheetService.withPeriodRate(timesheet(period("p1", 1, null)), "nope", "std"))
                .hasMessageContaining("Period nope not found");
    }

    @Test
    void estimatedTotalIsZeroForStudentsOrWithoutClient() {
        when(repository.findById("student")).thenReturn(Optional.of(timesheet().toBuilder().student(true).build()));
        when(repository.findById("no-client")).thenReturn(Optional.of(timesheet().toBuilder().clientId(null).build()));
        assertThat(service.estimateTotal("student")).isZero();
        assertThat(service.estimateTotal("no-client")).isZero();
        verifyNoInteractions(clientService);
    }

    @Test
    void estimatedTotalUsesTheRateOfEachPeriod() {
        // 7h at 50 + 7h at 100
        when(repository.findById("ts-1"))
                .thenReturn(Optional.of(timesheet(period("p1", 1, "std"), period("p2", 2, "sup"))));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        assertThat(service.estimateTotal("ts-1")).isEqualByComparingTo("1050.00");
    }

    @Test
    void clientWithoutAnyRateEstimatesZero() {
        var noRate = BillableClient.builder().rates(List.of()).build();
        assertThat(TimesheetService.computeTotal(timesheet(period("p1", 1, null)), noRate)).isZero();
    }

    @Test
    void nothingWorkedProducesASingleEmptyRow() {
        var rows = TimesheetService.buildInvoiceRows(timesheet(), client());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAmount()).isZero();
        assertThat(rows.getFirst().getRate()).isEqualByComparingTo("50");
    }

    @Test
    void invoiceGenerationGuards() {
        when(repository.findById("student")).thenReturn(Optional.of(timesheet().toBuilder().student(true).build()));
        when(repository.findById("open")).thenReturn(Optional.of(timesheet()));
        when(repository.findById("invoiced")).thenReturn(
                Optional.of(timesheet().toBuilder().closed(true).uploadId("u").invoiceId("already").build()));

        assertThatThrownBy(() -> service.generateInvoiceFromTimesheet("student")).hasMessageContaining("student");
        assertThatThrownBy(() -> service.generateInvoiceFromTimesheet("open")).hasMessageContaining("must be closed");
        assertThatThrownBy(() -> service.generateInvoiceFromTimesheet("invoiced"))
                .hasMessageContaining("already generated");
        verify(invoiceService, never()).generateInvoice(any());
    }

    @Test
    void invoiceIsGeneratedFromTheLatestTemplateAndTheRatesOfTheClient() {
        var ts = timesheet(period("p1", 1, "std"), period("p2", 2, "sup")).toBuilder().closed(true).uploadId("pdf")
                .build();
        when(repository.findById("ts-1")).thenReturn(Optional.of(ts));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        var old = InvoiceFreemarkerTemplate.builder().id("old").dateCreation(new Date(1000)).build();
        var latest = InvoiceFreemarkerTemplate.builder().id("latest").dateCreation(new Date(5000)).build();
        when(invoiceService.listTemplates()).thenReturn(List.of(old, latest));
        when(invoiceService.newInvoiceFromNothing())
                .thenReturn(InvoiceGeneration.builder().billTo(new BillTo()).build());
        when(invoiceService.generateInvoice(any()))
                .thenAnswer(a -> a.getArgument(0, InvoiceGeneration.class).toBuilder().id("invoice-1").build());
        ReflectionTestUtils.setField(service, "appendTimesheetToInvoice", true);

        var result = service.generateInvoiceFromTimesheet("ts-1");

        assertThat(result.getInvoiceId()).isEqualTo("invoice-1");
        var captor = ArgumentCaptor.forClass(InvoiceGeneration.class);
        verify(invoiceService).generateInvoice(captor.capture());
        var invoice = captor.getValue();
        assertThat(invoice.getFreemarkerTemplateId()).isEqualTo("latest");
        assertThat(invoice.getTimesheetId()).isEqualTo("ts-1");
        assertThat(invoice.getTaxRate()).isEqualByComparingTo("21");
        assertThat(invoice.getMaxDaysToPay()).isEqualTo(30);
        assertThat(invoice.getBillTo().getClientName()).isEqualTo("ACME");
        assertThat(invoice.getBillTo().getVatNumber()).isEqualTo("BE0123456749");
        assertThat(invoice.getBillTo().getEmailAddress()).isEqualTo("acme@example.com");
        assertThat(invoice.getInvoiceTable()).hasSize(2);
        assertThat(invoice.getSubTotal()).isEqualByComparingTo("1050.00");
    }

    @Test
    void timesheetIsNotAppendedToInvoiceByDefault() {
        var ts = timesheet(period("p1", 1, "std")).toBuilder().closed(true).uploadId("pdf").build();
        when(repository.findById("ts-1")).thenReturn(Optional.of(ts));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        when(invoiceService.listTemplates()).thenReturn(List.of(InvoiceFreemarkerTemplate.builder().id("t").build()));
        when(invoiceService.newInvoiceFromNothing())
                .thenReturn(InvoiceGeneration.builder().billTo(new BillTo()).build());
        when(invoiceService.generateInvoice(any())).thenAnswer(a -> a.getArgument(0));

        service.generateInvoiceFromTimesheet("ts-1");

        var captor = ArgumentCaptor.forClass(InvoiceGeneration.class);
        verify(invoiceService).generateInvoice(captor.capture());
        assertThat(captor.getValue().getTimesheetId()).isNull();
    }

    @Test
    void invoiceGenerationRequiresAtLeastOneTemplate() {
        var ts = timesheet(period("p1", 1, "std")).toBuilder().closed(true).uploadId("pdf").build();
        when(repository.findById("ts-1")).thenReturn(Optional.of(ts));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        when(invoiceService.listTemplates()).thenReturn(List.of());
        assertThatThrownBy(() -> service.generateInvoiceFromTimesheet("ts-1")).hasMessageContaining("template");
    }

    @Test
    void closingGeneratesUploadsAndAnnouncesThePdf() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet(period("p1", 1, "std"))));
        when(pdfService.timesheetToPdf(any())).thenReturn(new byte[] { 1, 2, 3 });
        when(uploadService.upload(any(MultipartFile.class), eq("ts-1"), anyBoolean())).thenReturn("upload-1");

        service.closeTimesheet("ts-1");

        var captor = ArgumentCaptor.forClass(Timesheet.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isClosed()).isTrue();
        assertThat(captor.getValue().getUploadId()).isEqualTo("upload-1");
        assertThat(lastEvent()).isInstanceOfSatisfying(TimesheetReleased.class, e -> {
            assertThat(e.getUploadId()).isEqualTo("upload-1");
            assertThat(e.getTimesheetId()).isEqualTo("ts-1");
            assertThat(e.getClientName()).isEqualTo("ACME");
            assertThat(e.getPeriod()).isEqualTo("10/2026");
        });
        verify(notificationService).sendEvent(eq("New Timesheet Ready (10/2026)"), eq("CLOSED_TIMESHEET"), eq("ts-1"));
    }

    @Test
    void closingTwiceFails() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet().toBuilder().closed(true).build()));
        assertThatThrownBy(() -> service.closeTimesheet("ts-1")).hasMessageContaining("already closed");
        verifyNoInteractions(pdfService);
    }

    @Test
    void reopeningRemovesPdfAndPendingInvoice() {
        when(repository.findById("ts-1")).thenReturn(
                Optional.of(timesheet().toBuilder().closed(true).uploadId("upload-1").invoiceId("invoice-1").build()));

        service.reopenTimesheet("ts-1");

        verify(uploadService).deleteByCorrelationId("ts-1");
        verify(invoiceService).deleteByTimesheetIdAndArchivedIsFalse("ts-1");
        var captor = ArgumentCaptor.forClass(Timesheet.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isClosed()).isFalse();
        assertThat(captor.getValue().getUploadId()).isNull();
        assertThat(captor.getValue().getInvoiceId()).isNull();
        assertThat(lastEvent()).isInstanceOf(TimesheetReopened.class);
    }

    @Test
    void reopeningAnOpenTimesheetFails() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet()));
        assertThatThrownBy(() -> service.reopenTimesheet("ts-1")).hasMessageContaining("not closed");
        verify(uploadService, never()).deleteByCorrelationId(anyString());
    }

    @Test
    void onlyOpenTimesheetsCanBeDeleted() {
        when(repository.findById("closed"))
                .thenReturn(Optional.of(timesheet().toBuilder().id("closed").closed(true).build()));
        service.deleteById("closed");
        verify(repository, never()).deleteById(anyString());

        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet()));
        service.deleteById("ts-1");
        verify(repository).deleteById("ts-1");
        verify(uploadService).deleteByCorrelationId("ts-1");
        assertThat(lastEvent()).isInstanceOf(TimesheetDeleted.class);
    }

    @Test
    void settingsAreTakenFromTheForm() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet()));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        var form = TimesheetSettingsForm.builder().timesheetId("ts-1").clientId("client-1")
                .maxHoursPerDay(new BigDecimal("9")).minHoursPerDay(new BigDecimal("4")).build();

        var saved = service.updateSettings(form);

        assertThat(saved.getSettings().getMaxHoursPerDay()).isEqualByComparingTo("9");
        assertThat(saved.getSettings().getMinHoursPerDay()).isEqualByComparingTo("4");
        assertThat(saved.getSettings().getDefaultProjectName()).isEqualTo("Project X");
        assertThat(saved.getClientName()).isEqualTo("ACME");
    }

    @Test
    void timesheetsOfFinishedContractsAreHidden() {
        var active = timesheet().toBuilder().id("a").clientName("ACME").build();
        var done = timesheet().toBuilder().id("d").clientName("Old Corp").build();
        when(repository.findAll()).thenReturn(List.of(active, done));
        when(clientService.findByContractStatus(ContractStatus.DONE))
                .thenReturn(List.of(BillableClient.builder().name("Old Corp").build()));

        var grouped = service.findAllGroupedByYearAndClientNameFilterClientDisabled();
        assertThat(grouped).containsOnlyKeys(2026);
        assertThat(grouped.get(2026)).containsOnlyKeys("ACME");

        assertThat(service.findAllGroupedByYearAndClientName().get(2026)).containsOnlyKeys("ACME", "Old Corp");
    }

    @Test
    void totalToBeInvoicedThisMonthSkipsStudents() {
        var ts = timesheet(period("p1", 1, "std"));
        var student = ts.toBuilder().id("student").student(true).build();
        when(repository.findByYearMonth(any(YearMonth.class))).thenReturn(List.of(ts, student));
        when(clientService.findById("client-1")).thenReturn(Optional.of(client()));
        assertThat(service.estimateTotalToBeInvoicedThisMonth()).isEqualByComparingTo("350.00");
    }

    @Test
    void oldUploadIdIsReplaced() {
        var ts = timesheet().toBuilder().uploadId("old").build();
        when(repository.findById("ts-1")).thenReturn(Optional.of(ts));
        service.updateOldId("ts-1", "old", "new");
        var captor = ArgumentCaptor.forClass(Timesheet.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUploadId()).isEqualTo("new");

        service.updateOldId("ts-1", "unrelated", "new");
        verify(repository).save(any()); // still only once
    }

    @Test
    void correlationLabels() {
        when(repository.findById("ts-1")).thenReturn(Optional.of(timesheet()));
        when(repository.findAllById(List.of("ts-1"))).thenReturn(List.of(timesheet()));
        assertThat(service.getCorrelationLabel("ts-1")).isEqualTo("Timesheet 10/2026");
        assertThat(service.getCorrelationLabel("unknown")).isNull();
        assertThat(service.getCorrelationLabels(List.of("ts-1"))).containsEntry("ts-1", "Timesheet 10/2026");
    }
}
