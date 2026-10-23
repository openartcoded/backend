package tech.artcoded.websitev2.rest.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestUtilTest {
    @Test
    void downloadsSetContentTypeDispositionAndPayload() throws Exception {
        byte[] payload = { 1, 2, 3 };
        var response = RestUtil.transformToByteArrayResource("report.pdf", "application/pdf", payload);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"report.pdf\"");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getByteArray()).containsExactly(payload);
    }

    @Test
    void missingDownloadReturnsBadRequest() {
        assertThat(RestUtil.transformToByteArrayResource("x", "text/plain", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void multipartToJsonUsesUtf8AndHandlesNull() {
        assertThat(RestUtil.FILE_TO_JSON.apply(null)).isEqualTo("{}");
        var file = new MockMultipartFile("file", "data.json", "application/json",
                "{\"é\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(RestUtil.FILE_TO_JSON.apply(file)).isEqualTo("{\"é\":true}");
    }

    @Test
    void clientAddressPrefersForwardedHeaderThenRealIpThenRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.2, 10.0.0.1", null);
        when(request.getHeader("X-Real-IP")).thenReturn("203.0.113.3", null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertThat(RestUtil.getClientIP(request)).isEqualTo("198.51.100.2");
        assertThat(RestUtil.getClientIP(request)).isEqualTo("203.0.113.3");
        assertThat(RestUtil.getClientIP(request)).isEqualTo("127.0.0.1");
    }
}
