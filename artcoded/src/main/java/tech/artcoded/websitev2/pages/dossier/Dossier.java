package tech.artcoded.websitev2.pages.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static java.util.Optional.ofNullable;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Dossier implements Serializable {
  private static final long serialVersionUID = 1L;
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date creationDate = new Date();
  @Builder.Default
  private Date updatedDate = new Date();
  private String name;
  private String description;
  @Builder.Default
  private Set<String> feeIds = Set.of();
  @Builder.Default
  private Set<String> invoiceIds = Set.of();
  @Builder.Default
  private Set<String> documentIds = Set.of();
  private boolean closed;
  private Date closedDate;
  private Date backupDate;
  private BigDecimal tvaDue;
  @Builder.Default
  private List<TvaAdvancePayment> advancePayments = List.of();
  private boolean recalledForModification;
  private Date recalledForModificationDate;
  @Builder.Default
  private boolean bookmarked = false;
  private Date bookmarkedDate;

  private String dossierUploadId;

  // if it was imported from an old system
  private boolean imported;
  private Date importedDate;

  @Transient
  public BigDecimal getTvaToBePaid() {
    return getTotalAdvancePayments()
        .subtract(ofNullable(tvaDue).orElse(BigDecimal.ZERO));

  }

  @Transient
  public BigDecimal getTotalAdvancePayments() {
    return ofNullable(advancePayments)
        .stream()
        .flatMap(Collection::stream)
        .map(TvaAdvancePayment::getAdvance)
        .reduce(BigDecimal::add)
        .orElse(BigDecimal.ZERO);
  }
}
