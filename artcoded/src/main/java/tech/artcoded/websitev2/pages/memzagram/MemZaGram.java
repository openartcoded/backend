package tech.artcoded.websitev2.pages.memzagram;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class MemZaGram {

    @Id
    private String id;

    @Builder.Default
    private Date createdDate = new Date();

    private Date updatedDate;

    private boolean visible;

    private Date dateOfVisibility;

    private String title;
    private String description;

    private String imageUploadId;
    private String thumbnailUploadId;

    private long viewsCount;

}
