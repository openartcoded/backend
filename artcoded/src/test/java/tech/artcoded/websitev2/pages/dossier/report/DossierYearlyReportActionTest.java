package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportService.ReportFile;

class DossierYearlyReportActionTest {
    private final DossierReportService service = mock(DossierReportService.class);
    private final DossierYearlyReportAction action = new DossierYearlyReportAction(service);

    private static ReportFile file(int year) {
        return new ReportFile("annual-report-%s.pdf".formatted(year), new byte[] { 1 }, DossierReport.builder()
                .annual(true).year(year).insights(List.of(new DossierReport.Insight("good", "Best year"))).build());
    }

    @Test
    void byDefaultThePreviousYear() {
        var previous = LocalDate.now(DossierReportService.ZONE).getYear() - 1;
        var file = file(previous);
        when(service.generateYear(previous)).thenReturn(file);

        var result = action.run(DossierYearlyReportAction.defaultMetadata().getAllowedParameters());

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        assertThat(result.getMessages()).contains("report annual-report-%s.pdf sent".formatted(previous), "Best year");
        verify(service).send(file, "Annual report %s".formatted(previous));
    }

    @Test
    void anotherYear() {
        when(service.generateYear(2023)).thenReturn(file(2023));

        var result = action.run(
                List.of(ActionParameter.builder().key(DossierYearlyReportAction.PARAMETER_YEAR).value("2023").build()));

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        verify(service).generateYear(2023);
    }

    @Test
    void aWrongYearFails() {
        var result = action.run(List.of(
                ActionParameter.builder().key(DossierYearlyReportAction.PARAMETER_YEAR).value("last year").build()));

        assertThat(result.getStatus()).isEqualTo(StatusType.FAILURE);
        verify(service, never()).generateYear(anyInt());
        verify(service, never()).send(any(), anyString());
    }

    @Test
    void theTwentiethOfJanuary() {
        var cron = CronExpression.parse(DossierYearlyReportAction.DEFAULT_CRON);
        assertThat(cron.next(LocalDateTime.of(2026, 11, 10, 12, 0))).isEqualTo(LocalDateTime.of(2027, 1, 20, 8, 23));
    }
}
