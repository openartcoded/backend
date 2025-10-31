package tech.artcoded.websitev2.pages.fee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Label implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Builder.Default
    private String id = IdGenerators.get();

    private String colorHex;
    private String description; // todo use that, eventually
    private String name;
    private BigDecimal priceHVAT;
    private BigDecimal vat;
    private boolean noDefaultPrice;
    private boolean hidden;
}
