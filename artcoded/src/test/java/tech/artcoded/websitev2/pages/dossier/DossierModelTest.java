package tech.artcoded.websitev2.pages.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import tech.artcoded.websitev2.pages.fee.Fee;

class DossierModelTest {

    private static TvaAdvancePayment advance(String amount) {
        return TvaAdvancePayment.builder().datePaid(new Date()).advance(amount == null ? null : new BigDecimal(amount))
                .build();
    }

    private static Fee fee(String priceHVAT) {
        return Fee.builder().priceHVAT(priceHVAT == null ? null : new BigDecimal(priceHVAT)).build();
    }

    @Test
    void tvaToBePaidIsTheAdvancesMinusTheDueAmount() {
        var dossier = Dossier.builder().tvaDue(new BigDecimal("1000.50"))
                .advancePayments(List.of(advance("300"), advance("200.25"))).build();

        assertThat(dossier.getTotalAdvancePayments()).isEqualByComparingTo("500.25");
        assertThat(dossier.getTvaToBePaid()).isEqualByComparingTo("-500.25");
    }

    @Test
    void newDossierHasNothingToPay() {
        var dossier = Dossier.builder().build();

        assertThat(dossier.getTotalAdvancePayments()).isEqualByComparingTo("0");
        assertThat(dossier.getTvaToBePaid()).isEqualByComparingTo("0");
    }

    @Test
    void legacyDossierWithoutListsDoesNotFail() {
        var dossier = Dossier.builder().advancePayments(null).tvaDue(null).build();

        assertThat(dossier.getTotalAdvancePayments()).isEqualByComparingTo("0");
        assertThat(dossier.getTvaToBePaid()).isEqualByComparingTo("0");
    }

    @Test
    void incompleteAdvancePaymentsAreIgnored() {
        var payments = new ArrayList<TvaAdvancePayment>();
        payments.add(advance("100"));
        payments.add(advance(null)); // amount not filled in yet
        payments.add(null);
        var dossier = Dossier.builder().tvaDue(new BigDecimal("40")).advancePayments(payments).build();

        assertThat(dossier.getTotalAdvancePayments()).isEqualByComparingTo("100");
        assertThat(dossier.getTvaToBePaid()).isEqualByComparingTo("60");
    }

    @Test
    void summaryTotalsExpensesPerTagRoundedDown() {
        var summary = DossierSummary.builder().name("Q1")
                .totalExpensesPerTag(Map.of("TRAIN", List.of(fee("10.005"), fee("5.10")), "FOOD", List.of(fee("3"))))
                .build();

        assertThat(summary.getComputedTotalExpensesPerTag()).containsOnlyKeys("FOOD", "TRAIN");
        assertThat(summary.getComputedTotalExpensesPerTag().get("TRAIN")).isEqualByComparingTo("15.10");
        assertThat(summary.getComputedTotalExpensesPerTag().get("FOOD")).isEqualByComparingTo("3.00");
        assertThat(summary.getTotalExpenses()).isEqualByComparingTo("18.10");
        // stable order for the front-end
        assertThat(summary.getComputedTotalExpensesPerTag().keySet()).containsExactly("FOOD", "TRAIN");
    }

    @Test
    void summaryIgnoresExpensesNotYetPriced() {
        var fees = new ArrayList<Fee>();
        fees.add(fee("12.50"));
        fees.add(fee(null));
        fees.add(null);
        var perTag = new HashMap<String, List<Fee>>();
        perTag.put("OFFICE", fees);
        perTag.put("EMPTY", null);

        var summary = DossierSummary.builder().totalExpensesPerTag(perTag).build();

        assertThat(summary.getComputedTotalExpensesPerTag().get("OFFICE")).isEqualByComparingTo("12.50");
        assertThat(summary.getComputedTotalExpensesPerTag().get("EMPTY")).isEqualByComparingTo("0");
        assertThat(summary.getTotalExpenses()).isEqualByComparingTo("12.50");
    }

    @Test
    void summaryWithoutExpensesOrDossier() {
        var summary = DossierSummary.builder().name("empty").build();

        assertThat(summary.getComputedTotalExpensesPerTag()).isEmpty();
        assertThat(summary.getTotalExpenses()).isEqualByComparingTo("0");
        assertThat(summary.getClosedDate()).isNull();
    }

    @Test
    void summaryExposesTheClosedDateOfItsDossier() {
        var closed = new Date();
        var summary = DossierSummary.builder().dossier(Dossier.builder().closed(true).closedDate(closed).build())
                .build();

        assertThat(summary.getClosedDate()).isEqualTo(closed);
    }

    @Test
    void summaryJsonHidesInternalsButExposesComputedTotals() throws Exception {
        var summary = DossierSummary.builder().name("Q2").totalEarnings(new BigDecimal("1000"))
                .dossier(Dossier.builder().name("secret").build())
                .totalExpensesPerTag(Map.of("TRAIN", List.of(fee("20")))).build();

        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(summary));

        assertThat(json.has("dossier")).isFalse();
        assertThat(json.has("totalExpensesPerTag")).isFalse();
        assertThat(json.get("name").asText()).isEqualTo("Q2");
        assertThat(json.get("computedTotalExpensesPerTag").get("TRAIN").decimalValue()).isEqualByComparingTo("20");
        assertThat(json.get("totalExpenses").decimalValue()).isEqualByComparingTo("20");
    }
}
