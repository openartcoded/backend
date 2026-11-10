package tech.artcoded.websitev2.filesearch;

import java.util.Date;
import java.util.List;

/**
 * A page of the file search (v2), the uploads of the hits resolved: what the backoffice shows. The paths of the
 * files in the file search service are not exposed.
 */
public record FileSearchResult(List<Hit> hits, int page, int perPage, boolean hasMore, long totalMatches,
        long tookMs) {

    /**
     * A matching file (or a matching row / chunk when not grouped by file).
     *
     * @param fileName
     *            the file indexed: the upload, or a file of an uploaded archive
     * @param extracted
     *            the file comes from an uploaded archive (fileName is in originFileName)
     * @param snippetHtml
     *            html escaped, the matching words in &lt;b&gt;
     * @param modifiedAt
     *            yyyy-mm-dd
     * @param upload
     *            the upload (the archive for an extracted file), null when it isn't an upload of the backoffice
     */
    public record Hit(double score, String fileName, String originFileName, boolean extracted, String kind,
            String extension, String sheetName, List<String> positions, List<String> context, String content,
            String snippetHtml, String modifiedAt, int matches, Upload upload) {
    }

    /** the upload of a hit, with the label of what it belongs to (an expense, an invoice...) */
    public record Upload(String id, String originalFilename, String contentType, long size, Date creationDate,
            String correlationId, String correlationLabel) {
    }
}
