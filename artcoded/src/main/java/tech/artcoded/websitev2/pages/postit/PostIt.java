package tech.artcoded.websitev2.pages.postit;

import java.io.Serializable;
import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PostIt implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Builder.Default()
    private String id = IdGenerators.get();
    private String note;
    @Builder.Default()
    private Date creationDate = new Date();
    private Date updatedDate;

}
