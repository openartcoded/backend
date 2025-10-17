package tech.artcoded.websitev2.pages.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Date;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Portfolio {
    @Id
    @Builder.Default
    private String id = IdGenerators.get();
    @Builder.Default
    private Date dateCreation = new Date();
    @Builder.Default
    private Date updatedDate = new Date();
    private String name;
    @Builder.Default
    private Set<Tick> ticks = Set.of();
    private boolean principal;
}
