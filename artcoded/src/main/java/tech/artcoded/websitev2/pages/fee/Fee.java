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
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Fee implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @Builder.Default
    private String id = IdGenerators.get();
    @Builder.Default
    private Date dateCreation = new Date();
    @Builder.Default
    private Date updatedDate = new Date();
    private Date date;
    private String subject;
    private String body;
    private List<String> attachmentIds;
    // 2025-10-16 23:04 after invoices
    // added it to expenses
    private boolean bookmarked;
    private Date bookmarkedDate;
    private boolean archived;
    private Date archivedDate;
    private String tag;
    private BigDecimal priceHVAT;
    private BigDecimal vat;

    // wheiter it was imported from an old system
    private boolean imported;
    private Date importedDate;

    public BigDecimal getPriceTot() {
        return Stream.of(priceHVAT, vat).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
