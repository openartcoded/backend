package tech.artcoded.websitev2.upload.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FileUploadServiceV2ExtensionTest {
    @ParameterizedTest
    @CsvSource({ "image/jpeg, .jpg", "image/jpg, .jpg", "image/png, .png", "image/gif, .gif", "image/bmp, .bmp",
            "image/tiff, .tiff", "image/webp, .webp", "image/vnd.microsoft.icon, .ico", "image/svg+xml, .svg",
            "image/heif, .heif", "image/heic, .heic", "image/jxl, .jxl", "application/pdf, .pdf", "text/html, .html" })
    void knownContentTypes(String contentType, String extension) {
        assertThat(FileUploadServiceV2.getExtension(contentType)).isEqualTo(extension);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "application/octet-stream", "IMAGE/PNG", "text/plain" })
    void unknownContentTypesHaveNoExtension(String contentType) {
        // null used to throw a NullPointerException in the old switch statement
        assertThat(FileUploadServiceV2.getExtension(contentType)).isEmpty();
    }
}
