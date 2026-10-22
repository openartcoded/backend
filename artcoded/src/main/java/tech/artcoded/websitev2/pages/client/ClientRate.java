package tech.artcoded.websitev2.pages.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.math.BigDecimal;

/**
 * A rate a client can be billed with. A client has one or many of them, the
 * first one being the default.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ClientRate {
    @Builder.Default
    private String id = IdGenerators.get();
    /**
     * optional, when present it is added to the description of the invoice row
     */
    private String label;
    private BigDecimal rate;
    private RateType rateType;

    @Transient
    public boolean hasLabel() {
        return StringUtils.isNotBlank(label);
    }
}
