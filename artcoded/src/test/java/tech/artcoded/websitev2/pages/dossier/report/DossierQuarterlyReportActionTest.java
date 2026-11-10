package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportService.ReportFile;

class DossierQuarterlyReportActionTest {
    private final DossierReportService service = mock(DossierReportService.class);
    private final DossierQuarterlyReportAction action = new DossierQuarterlyReportAction(service);
    private static final ReportFile FILE = new ReportFile("report-Q3-2026-2026-07_2026-09.pdf", new byte[] { 1 },
            DossierReport.builder().insights(List.of(new DossierReport.Insight("good", "Best July – September")))
                    .build());

    @Test
    void theLastThreeCompleteMonths() {
        var october = DossierQuarterlyReportAction.period(LocalDate.of(2026, 10, 10));
        assertThat(october.from()).isEqualTo(YearMonth.of(2026, 7));
        assertThat(october.to()).isEqualTo(YearMonth.of(2026, 9));
        var january = DossierQuarterlyReportAction.period(LocalDate.of(2027, 1, 10));
        assertThat(january.from()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(january.to()).isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void theReportOfTheDossierClosedLastIsMailed() {
        var q3 = Dossier.builder().id("q3").name("Q3-2026").closed(true).build();
        when(service.lastClosedDossier()).thenReturn(Optional.of(q3));
        when(service.generate(eq(q3), any(YearMonth.class), any(YearMonth.class))).thenReturn(FILE);

        var result = action.run(List.of());

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        assertThat(result.getMessages()).contains("report report-Q3-2026-2026-07_2026-09.pdf sent",
                "Best July – September");
        var period = DossierQuarterlyReportAction.period(LocalDate.now(DossierReportService.ZONE));
        verify(service).generate(q3, period.from(), period.to());
        verify(service).send(FILE);
        verify(service, never()).activeDossier();
    }

    @Test
    void theOpenDossierWhenNoneIsClosedYet() {
        var open = Dossier.builder().id("open").build();
        when(service.lastClosedDossier()).thenReturn(Optional.empty());
        when(service.activeDossier()).thenReturn(Optional.of(open));
        when(service.generate(eq(open), any(YearMonth.class), any(YearMonth.class))).thenReturn(FILE);

        assertThat(action.run(List.of()).getStatus()).isEqualTo(StatusType.SUCCESS);
        verify(service).send(FILE);
    }

    @Test
    void nothingWithoutDossier() {
        when(service.lastClosedDossier()).thenReturn(Optional.empty());
        when(service.activeDossier()).thenReturn(Optional.empty());

        var result = action.run(List.of());

        assertThat(result.getMessages()).containsExactly("there is no dossier: no report");
        verify(service, never()).send(any());
    }

    @Test
    void noParameterAndEveryQuarter() {
        assertThat(DossierQuarterlyReportAction.defaultMetadata().getAllowedParameters()).isEmpty();
        var cron = CronExpression.parse(DossierQuarterlyReportAction.DEFAULT_CRON);
        assertThat(cron.next(LocalDateTime.of(2026, 11, 10, 11, 0))).isEqualTo(LocalDateTime.of(2027, 1, 10, 8, 17));
        assertThat(cron.next(LocalDateTime.of(2027, 1, 10, 9, 0))).isEqualTo(LocalDateTime.of(2027, 4, 10, 8, 17));
    }
}
