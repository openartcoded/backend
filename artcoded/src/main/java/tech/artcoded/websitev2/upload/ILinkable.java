package tech.artcoded.websitev2.upload;

import java.util.List;
import java.util.Optional;

/**
 * links an upload to a document in a human way
 * Added as correltation id is meaningless
 * Every document type (expense, invoice,...) with ids that can be correlated
 * to a file will provide a specific label to better display uploads
 * FIXME experiments added on 17/10/2025, getAdditionalMetadataForCorrelationId
 * is not yet used
 * but could be in the future.
 *
 *
 */
public interface ILinkable {
  public record AdditionalMetadata(String key, String value) {
  }

  Optional<String> getCorrelationLabel(String correlationId);

  default List<AdditionalMetadata> getAdditionalMetadataForCorrelationId(String correlationId) {
    return List.of();
  }

}
