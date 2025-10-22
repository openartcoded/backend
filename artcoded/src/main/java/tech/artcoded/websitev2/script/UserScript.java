package tech.artcoded.websitev2.script;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import tech.artcoded.websitev2.utils.helper.IdGenerators;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Document
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class UserScript {
    @Id
    @Builder.Default
    private String id = IdGenerators.get();

    @Builder.Default
    private Date creationDate = new Date();
    private Date updatedDate;

    private String content;

}
