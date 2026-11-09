package tech.artcoded.websitev2.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.pages.client.BillableClient;

class RateTypeTest {
    @Test
    void ublUnitCodesFollowUnEceRecommendation20() {
        assertThat(RateType.HOURS.getUBLRateType()).isEqualTo("HUR");
        assertThat(RateType.DAYS.getUBLRateType()).isEqualTo("DAY");
    }

    @Test
    void labelsAreHumanReadable() {
        assertThat(RateType.HOURS.getLabel()).isEqualTo("hour");
        assertThat(RateType.DAYS.getLabel()).isEqualTo("day");
    }

    @Test
    void billableClientDelegatesToRateTypeAndIsNullSafe() {
        assertThat(BillableClient.builder().rateType(RateType.DAYS).build().getUBLRateType()).isEqualTo("DAY");
        assertThat(BillableClient.builder().rateType(RateType.HOURS).build().getUBLRateType()).isEqualTo("HUR");
        assertThat(BillableClient.builder().rateType(null).build().getUBLRateType()).isNull();
    }

    @Test
    void billableClientVatNumbers() {
        var client = BillableClient.builder().vatNumber("BE 0123.456.749").build();
        assertThat(client.getCleanVatNumber()).isEqualTo("BE0123456749");
        assertThat(client.getCompanyNumber()).isEqualTo("0123456749");
        assertThat(BillableClient.builder().vatNumber(null).build().getCleanVatNumber()).isEqualTo("0000000000");
    }
}
