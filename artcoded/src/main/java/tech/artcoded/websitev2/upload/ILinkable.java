package tech.artcoded.websitev2.upload;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * links an upload to a document in a human way Added as correltation id is meaningless Every document type (expense,
 * invoice,...) with ids that can be correlated to a file will provide a specific label to better display uploads FIXME
 * experiments added on 17/10/2025, getAdditionalMetadataForCorrelationId is not yet used but could be in the future.
 */
public interface ILinkable {

    Logger LOG = LoggerFactory.getLogger(ILinkable.class);

    public record AdditionalMetadata(String key, String value) {
    }

    void updateOldId(String correlationId, String oldId, String newId);

    String getCorrelationLabel(String correlationId);

    Map<String, String> getCorrelationLabels(Collection<String> correlationIds);

    default Optional<String> getCorrelationLabelWithNonNullCorrelationId(String correlationId) {
        if (StringUtils.isBlank(correlationId)) {
            LOG.warn("calling getCorrelationLabelWithNonNullCorrelationId with null correlation id!");
            return Optional.empty();
        }
        return Optional.ofNullable(this.getCorrelationLabel(correlationId));
    }

    default List<AdditionalMetadata> getAdditionalMetadataForCorrelationId(String correlationId) {
        return List.of();
    }

}
