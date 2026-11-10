package tech.artcoded.websitev2.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.openapitools.client.api.TemplateRoutesApi;
import org.openapitools.client.model.Context;
import org.openapitools.client.model.RenderRequest;
import org.springframework.core.io.ClassPathResource;

class OaTemplateRendererTest {
    private final OaTemplateService templateService = mock(OaTemplateService.class);
    private final TemplateRoutesApi api = mock(TemplateRoutesApi.class);
    private final HtmlToPdfRenderer htmlToPdf = mock(HtmlToPdfRenderer.class);
    private final OaTemplateRenderer renderer = new OaTemplateRenderer(templateService, api, htmlToPdf);

    @TempDir
    Path tmp;

    /** a model like the entities: fields, plus a value only exposed through a getter */
    public static class Line {
        private final BigDecimal rate;
        private final BigDecimal amount;
        private final Date date;

        Line(BigDecimal rate, BigDecimal amount, Date date) {
            this.rate = rate;
            this.amount = amount;
            this.date = date;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public Date getDate() {
            return date;
        }

        public BigDecimal getTotal() {
            return rate.multiply(amount);
        }
    }

    private static final Map<String, Object> DATA = Map.of("line",
            new Line(new BigDecimal("100"), new BigDecimal("2"), new Date(1662921288000L)), "name", "ACME");

    @Test
    void aV1XmlTemplateIsRenderedByFreemarkerWithoutPdf() throws Exception {
        var template = OaTemplate.builder().type(OaTemplateType.PEPPOL_INVOICE).version(OaTemplateVersion.V1).build();
        when(templateService.v1Source(template)).thenReturn("<x name=\"${name}\">${line.total}</x>");

        var xml = renderer.render(template, DATA, "f");

        assertThat(new String(xml, StandardCharsets.UTF_8)).isEqualTo("<x name=\"ACME\">200</x>");
        verifyNoInteractions(htmlToPdf, api);
    }

    @Test
    void aV1HtmlTemplateIsPrintedToPdfByTheBackend() throws Exception {
        var template = OaTemplate.builder().type(OaTemplateType.INVOICE).version(OaTemplateVersion.V1).build();
        when(templateService.v1Source(template)).thenReturn("<p>${name}</p>");
        when(htmlToPdf.render("<p>ACME</p>")).thenReturn(new byte[] { 1, 2 });

        assertThat(renderer.render(template, DATA, "f")).containsExactly(1, 2);
        verifyNoInteractions(api);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aV2TemplateIsRenderedByTheFileServiceWithPlainJson() throws Exception {
        var template = OaTemplate.builder().type(OaTemplateType.CV).fileServiceTemplateId("fs-1").build();
        var rendered = tmp.resolve("rendered.pdf");
        Files.write(rendered, new byte[] { 7 });
        when(api.render(any())).thenReturn(rendered.toFile());

        assertThat(renderer.render(template, DATA, "cv")).containsExactly(7);

        var captor = ArgumentCaptor.forClass(RenderRequest.class);
        verify(api).render(captor.capture());
        var request = captor.getValue();
        assertThat(request.getTemplateId()).isEqualTo("fs-1");
        assertThat(request.getTemplateContext()).isEqualTo(Context.CV);
        assertThat(request.getFileName()).isEqualTo("cv");
        var line = (Map<String, Object>) ((Map<String, Object>) request.getContext()).get("line");
        // computed through a getter, gson alone would miss it
        assertThat(((Number) line.get("total")).intValue()).isEqualTo(200);
        // iso-8601, parseable by the jinja date filters
        assertThat(line.get("date")).isInstanceOfSatisfying(String.class,
                d -> assertThat(d).startsWith("2022-09-11T18:34:48"));
        assertThat(rendered).as("temporary rendered file removed").doesNotExist();
        verifyNoInteractions(htmlToPdf);
    }

    @Test
    void theDefaultTemplateOfTheTypeIsRenderedWhateverItsVersion() throws Exception {
        var v1 = OaTemplate.builder().type(OaTemplateType.TIMESHEET).version(OaTemplateVersion.V1).build();
        when(templateService.getDefault(OaTemplateType.TIMESHEET)).thenReturn(Optional.of(v1));
        when(templateService.v1Source(v1)).thenReturn("${name}");
        when(htmlToPdf.render("ACME")).thenReturn(new byte[] { 3 });
        assertThat(renderer.renderDefault(OaTemplateType.TIMESHEET, DATA, "ts")).containsExactly(3);

        var v2 = OaTemplate.builder().type(OaTemplateType.SMS).fileServiceTemplateId("fs-sms").build();
        when(templateService.getDefault(OaTemplateType.SMS)).thenReturn(Optional.of(v2));
        var rendered = tmp.resolve("sms.txt");
        Files.writeString(rendered, "sms by the api");
        when(api.render(any())).thenReturn(rendered.toFile());
        assertThat(renderer.renderDefaultText(OaTemplateType.SMS, DATA)).isEqualTo("sms by the api");
    }

    @Test
    void withoutDefaultTheBundledTemplateIsRendered() {
        when(templateService.getDefault(any())).thenReturn(Optional.empty());
        Map<String, Object> actionResult = Map.of("startedDate", new Date(0), "finishedDate", new Date(0), "status",
                "SUCCESS", "messages", List.of("all done"));

        var sms = renderer.renderDefaultText(OaTemplateType.SMS,
                Map.of("actionKey", "CLEANUP", "actionResult", actionResult));

        assertThat(sms).contains("Action: CLEANUP").contains("all done");
        verifyNoInteractions(api);
    }

    @Test
    void aTextTemplateThatCannotBeRenderedFallsBackToTheBundledOne() throws Exception {
        var broken = OaTemplate.builder().type(OaTemplateType.EMAIL).fileServiceTemplateId("fs-email").build();
        when(templateService.getDefault(OaTemplateType.EMAIL)).thenReturn(Optional.of(broken));
        when(api.render(any())).thenThrow(new org.openapitools.client.ApiException("file service down"));

        var email = renderer.renderDefaultText(OaTemplateType.EMAIL,
                Map.of("subject", "Hello", "htmlBody", "<p>the body</p>", "attachments", List.of()));

        assertThat(email).contains("<p>the body</p>");
    }

    @Test
    void aDocumentWithoutDefaultUsesTheBundledTemplateToo() {
        when(templateService.getDefault(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> renderer.renderDefaultText(OaTemplateType.INVOICE, DATA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyTypeHasAnExistingBundledTemplate() {
        assertThat(OaTemplateType.values()).hasSize(10).allSatisfy(
                t -> assertThat(new ClassPathResource(t.bundledTemplate()).exists()).as(t.bundledTemplate()).isTrue());
    }
}
