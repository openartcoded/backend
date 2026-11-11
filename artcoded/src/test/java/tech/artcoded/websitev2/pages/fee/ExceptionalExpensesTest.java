package tech.artcoded.websitev2.pages.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.upload.IFileUploadService;

class ExceptionalExpensesTest {

    static Fee fee(String id, String price, ExceptionalStatus status) {
        return Fee.builder().id(id).priceHVAT(new BigDecimal(price)).vat(BigDecimal.ZERO).exceptional(status)
                .date(new Date()).build();
    }

    // usual expenses around 50-120, then taxes
    private final List<Fee> fees = List.of(fee("a", "50", null), fee("b", "60", null), fee("c", "80", null),
            fee("d", "100", null), fee("e", "120", null), fee("tax", "5000", null),
            fee("laptop", "1800", ExceptionalStatus.DENIED), fee("small", "90", ExceptionalStatus.CONFIRMED));

    @Test
    void theMedianIgnoresTheExpensesWithoutPrice() {
        assertThat(ExceptionalExpenses.median(List.of(fee("a", "10", null), fee("b", "30", null))))
                .isEqualByComparingTo("20");
        assertThat(ExceptionalExpenses.median(List.of(fee("a", "10", null), fee("b", "30", null), fee("c", "0", null),
                fee("d", "50", null)))).isEqualByComparingTo("30");
        assertThat(ExceptionalExpenses.median(List.of())).isEqualByComparingTo("0");
    }

    @Test
    void theThresholdIsFactorTimesTheMedianButAtLeastTheMinimum() {
        assertThat(ExceptionalExpenses.threshold(new BigDecimal("90"), new BigDecimal("5"), new BigDecimal("1000")))
                .isEqualByComparingTo("1000");
        assertThat(ExceptionalExpenses.threshold(new BigDecimal("400"), new BigDecimal("5"), new BigDecimal("1000")))
                .isEqualByComparingTo("2000");
    }

    @Test
    void theReportHasTheSuspectsAndTheReviewedOnes() {
        var report = ExceptionalExpenses.report(fees, new BigDecimal("5"), new BigDecimal("1000"));
        assertThat(report.median()).isEqualByComparingTo("95");
        assertThat(report.threshold()).isEqualByComparingTo("1000");
        // most expensive first, the small confirmed one is there too
        assertThat(report.expenses()).extracting(Fee::getId).containsExactly("tax", "laptop", "small");
    }

    @Test
    void exceptionalForTheFiguresConfirmedOrNotReviewedAndBig() {
        var threshold = new BigDecimal("1000");
        assertThat(ExceptionalExpenses.isExceptional(fee("tax", "5000", null), threshold)).isTrue();
        assertThat(ExceptionalExpenses.isExceptional(fee("laptop", "1800", ExceptionalStatus.DENIED), threshold))
                .isFalse();
        assertThat(ExceptionalExpenses.isExceptional(fee("small", "90", ExceptionalStatus.CONFIRMED), threshold))
                .isTrue();
        assertThat(ExceptionalExpenses.isExceptional(fee("a", "50", null), threshold)).isFalse();
    }

    @Test
    void thePriceVatIncludedIsCompared() {
        var fee = Fee.builder().priceHVAT(new BigDecimal("900")).vat(new BigDecimal("189")).build();
        assertThat(ExceptionalExpenses.isAboveThreshold(fee, new BigDecimal("1000"))).isTrue();
    }

    @Test
    void flagsSeveralExpensesAtOnceAndResets() {
        var repository = mock(FeeRepository.class);
        when(repository.save(any(Fee.class))).thenAnswer(a -> a.getArgument(0));
        when(repository.findById("tax")).thenReturn(Optional.of(fee("tax", "5000", null)));
        when(repository.findById("laptop")).thenReturn(Optional.of(fee("laptop", "1800", null)));
        when(repository.findById("gone")).thenReturn(Optional.empty());
        var service = new FeeService(repository, mock(LabelService.class), mock(IFileUploadService.class),
                mock(MongoTemplate.class), mock(ExposedEventService.class));

        var confirmed = service.updateExceptional(List.of("tax", "laptop", "tax", "gone"), ExceptionalStatus.CONFIRMED);
        assertThat(confirmed).extracting(Fee::getId).containsExactly("tax", "laptop");
        assertThat(confirmed).allSatisfy(f -> {
            assertThat(f.getExceptional()).isEqualTo(ExceptionalStatus.CONFIRMED);
            assertThat(f.getExceptionalDate()).isNotNull();
        });

        when(repository.findById("tax")).thenReturn(Optional.of(confirmed.getFirst()));
        var reset = service.updateExceptional(List.of("tax"), null);
        assertThat(reset.getFirst().getExceptional()).isNull();
        assertThat(reset.getFirst().getExceptionalDate()).isNull();
    }
}
