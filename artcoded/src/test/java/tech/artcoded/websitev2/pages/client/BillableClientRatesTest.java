package tech.artcoded.websitev2.pages.client;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.artcoded.websitev2.domain.common.RateType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public class BillableClientRatesTest {

    @Test
    public void normalizeAssignsIdsTrimsLabelsAndDropsIncompleteRates() {
        var received = Arrays.asList(
                ClientRate.builder().id(null).label("  Night  ").rate(new BigDecimal("80")).rateType(RateType.HOURS)
                        .build(),
                ClientRate.builder().id("keep").label("   ").rate(new BigDecimal("60")).rateType(RateType.DAYS).build(),
                ClientRate.builder().rate(null).rateType(RateType.HOURS).build(),
                ClientRate.builder().rate(BigDecimal.ONE).rateType(null).build(), null);

        var rates = BillableClient.normalizeRates(received, null, null);

        Assertions.assertThat(rates).hasSize(2);
        Assertions.assertThat(rates.get(0).getId()).isNotBlank();
        Assertions.assertThat(rates.get(0).getLabel()).isEqualTo("Night");
        Assertions.assertThat(rates.get(1).getId()).isEqualTo("keep");
        Assertions.assertThat(rates.get(1).getLabel()).isNull();
    }

    @Test
    public void normalizeFallsBackToLegacyRate() {
        var rates = BillableClient.normalizeRates(null, new BigDecimal("500"), RateType.DAYS);
        Assertions.assertThat(rates).hasSize(1);
        Assertions.assertThat(rates.get(0).getRate()).isEqualByComparingTo("500");
        Assertions.assertThat(rates.get(0).getRateType()).isEqualTo(RateType.DAYS);
        Assertions.assertThat(rates.get(0).getId()).isNotBlank();

        Assertions.assertThat(BillableClient.normalizeRates(List.of(), null, null)).isEmpty();
    }

    @Test
    public void ratesOrDefaultExposesLegacyRateForNotMigratedClients() {
        var client = BillableClient.builder().rate(new BigDecimal("10")).rateType(RateType.HOURS).build();
        Assertions.assertThat(client.getRatesOrDefault()).hasSize(1);
        Assertions.assertThat(BillableClient.builder().build().getRatesOrDefault()).isEmpty();
    }
}
