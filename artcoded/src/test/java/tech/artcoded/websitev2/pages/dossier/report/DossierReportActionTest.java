package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportService.ReportFile;

class DossierReportActionTest {
    private final DossierReportService service = mock(DossierReportService.class);
    private final DossierReportAction action = new DossierReportAction(service);
    private static final Dossier OPEN = Dossier.builder().id("open").name("2026").build();
    private static final ReportFile FILE = new ReportFile("report-2026-2026-10.pdf", new byte[] { 1 },
            DossierReport.builder().insights(List.of(new DossierReport.Insight("good", "Best October"))).build());

    @Test
    void byDefaultTheOpenDossierAndThePreviousMonth() {
        when(service.activeDossier()).thenReturn(Optional.of(OPEN));
        when(service.defaultMonth(OPEN)).thenReturn(YearMonth.of(2026, 10));
        when(service.generate(OPEN, YearMonth.of(2026, 10))).thenReturn(FILE);

        var result = action.run(DossierReportAction.defaultMetadata().getAllowedParameters());

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        assertThat(result.getMessages()).contains("report report-2026-2026-10.pdf sent", "Best October");
        verify(service).send(FILE);
    }

    @Test
    void anotherDossierAndMonth() {
        var closed = Dossier.builder().id("closed").closed(true).build();
        when(service.findDossier("closed")).thenReturn(Optional.of(closed));
        when(service.generate(closed, YearMonth.of(2025, 12))).thenReturn(FILE);

        var result = action.run(
                List.of(ActionParameter.builder().key(DossierReportAction.PARAMETER_DOSSIER_ID).value("closed").build(),
                        ActionParameter.builder().key(DossierReportAction.PARAMETER_MONTH).value(" 2025-12 ").build()));

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        verify(service).send(FILE);
        verify(service, never()).activeDossier();
    }

    @Test
    void aDossierWithoutMonthIsStillAMonthlyReport() {
        var closed = Dossier.builder().id("closed").closed(true).build();
        when(service.findDossier("closed")).thenReturn(Optional.of(closed));
        when(service.defaultMonth(closed)).thenReturn(YearMonth.of(2025, 12));
        when(service.generate(closed, YearMonth.of(2025, 12))).thenReturn(FILE);

        var result = action.run(
                List.of(ActionParameter.builder().key(DossierReportAction.PARAMETER_DOSSIER_ID).value("closed").build()));

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        verify(service, never()).generateWholeDossier(any());
        verify(service).send(FILE);
    }

    @Test
    void anUnknownDossierFails() {
        when(service.findDossier("nope")).thenReturn(Optional.empty());

        var result = action.run(
                List.of(ActionParameter.builder().key(DossierReportAction.PARAMETER_DOSSIER_ID).value("nope").build()));

        assertThat(result.getStatus()).isEqualTo(StatusType.FAILURE);
        verify(service, never()).send(any());
    }

    @Test
    void nothingToDoWithoutOpenDossier() {
        when(service.activeDossier()).thenReturn(Optional.empty());

        var result = action.run(List.of());

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        assertThat(result.getMessages()).containsExactly("there is no open dossier: no report");
        verify(service, never()).send(any());
    }

    @Test
    void aWrongMonthFails() {
        var result = action.run(
                List.of(ActionParameter.builder().key(DossierReportAction.PARAMETER_MONTH).value("october").build()));

        assertThat(result.getStatus()).isEqualTo(StatusType.FAILURE);
        verify(service, never()).send(any());
    }

    @Test
    void theTaskRunsTheTenthOfEveryMonth() {
        var cron = org.springframework.scheduling.support.CronExpression.parse(DossierReportAction.DEFAULT_CRON);
        var next = cron.next(java.time.LocalDateTime.of(2026, 11, 10, 9, 0));
        assertThat(next).isEqualTo(java.time.LocalDateTime.of(2026, 12, 10, 7, 47));
    }
}
