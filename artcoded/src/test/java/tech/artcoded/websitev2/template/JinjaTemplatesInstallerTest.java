package tech.artcoded.websitev2.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.openapitools.client.ApiException;
import org.openapitools.client.ApiResponse;
import org.openapitools.client.api.UploadRoutesApi;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.template.JinjaTemplatesInstaller.JinjaTemplate;

class JinjaTemplatesInstallerTest {
    private final OaTemplateService templateService = mock(OaTemplateService.class);
    private final OaTemplateRepository repository = mock(OaTemplateRepository.class);
    private final UploadRoutesApi uploadRoutesApi = mock(UploadRoutesApi.class);

    private JinjaTemplatesInstaller installer(Duration timeout) {
        return new JinjaTemplatesInstaller(templateService, repository, uploadRoutesApi, timeout, Duration.ofMillis(1));
    }

    private static ApiResponse<Void> pong() {
        return new ApiResponse<>(200, Map.of());
    }

    @Test
    void oneJinjaTemplatePerTypeShippedWithTheApplication() {
        assertThat(JinjaTemplatesInstaller.TEMPLATES).extracting(JinjaTemplate::type)
                .containsExactlyInAnyOrder(OaTemplateType.values());
        assertThat(JinjaTemplatesInstaller.TEMPLATES).allSatisfy(t -> {
            assertThat(new ClassPathResource(t.path()).exists()).as(t.path()).isTrue();
            assertThat(t.path()).as("the extension gives the content type").endsWith(t.type().extension());
            var source = OaTemplateRenderer.readClasspath(t.path());
            assertThat(source).as(t.path() + " is not FreeMarker anymore").doesNotContain("${", "<#", "</#");
        });
    }

    @Test
    void everyTemplateIsUploadedAsAV2DefaultOnceTheFileServiceIsUp() throws Exception {
        when(uploadRoutesApi.pingWithHttpInfo()).thenThrow(new ApiException("connection refused")).thenReturn(pong());
        when(templateService.save(any(), anyString(), anyString(), any(), eq(false), eq(true), any()))
                .thenAnswer(a -> OaTemplate.builder().name(a.getArgument(1)).type(a.getArgument(3)).build());

        var installed = installer(Duration.ofSeconds(10)).install();

        assertThat(installed).hasSize(OaTemplateType.values().length);
        verify(uploadRoutesApi, times(2)).pingWithHttpInfo();
        verify(templateService).save(isNull(), eq("Invoice 2027"), anyString(), eq(OaTemplateType.INVOICE), eq(false),
                eq(true), org.mockito.ArgumentMatchers.<MultipartFile> argThat(f -> {
                    try {
                        return "invoice-2027.html".equals(f.getOriginalFilename())
                                && new String(f.getBytes()).contains("{{ invoice.newInvoiceNumber }}");
                    } catch (Exception e) {
                        return false;
                    }
                }));
        verify(templateService).save(isNull(), eq("Calendar invite"), anyString(), eq(OaTemplateType.CALENDAR),
                eq(false), eq(true), org.mockito.ArgumentMatchers
                        .<MultipartFile> argThat(f -> "calendar.ics".equals(f.getOriginalFilename())));
    }

    @Test
    void aTemplateAlreadyInstalledIsOnlyMadeTheDefault() throws Exception {
        when(uploadRoutesApi.pingWithHttpInfo()).thenReturn(pong());
        var installed = OaTemplate.builder().id("cv-v2").name("CV 2027").type(OaTemplateType.CV)
                .version(OaTemplateVersion.V2).build();
        when(repository.findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(OaTemplateType.CV))
                .thenReturn(List.of(installed));
        when(templateService.setDefault("cv-v2")).thenReturn(Optional.of(installed));
        when(templateService.save(any(), anyString(), anyString(), any(), eq(false), eq(true), any()))
                .thenAnswer(a -> OaTemplate.builder().name(a.getArgument(1)).type(a.getArgument(3)).build());

        installer(Duration.ofSeconds(1)).install();

        verify(templateService).setDefault("cv-v2");
        verify(templateService, never()).save(any(), anyString(), any(), eq(OaTemplateType.CV), eq(false), eq(true),
                any());
    }

    @Test
    void theFixedReportIsANewTemplateNextToTheFirstOneInstalled() throws Exception {
        when(uploadRoutesApi.pingWithHttpInfo()).thenReturn(pong());
        // what _0004 installed in production
        var first = OaTemplate.builder().id("first").name("Dossier report").type(OaTemplateType.DOSSIER_REPORT)
                .version(OaTemplateVersion.V2).defaultTemplate(true).build();
        when(repository.findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(OaTemplateType.DOSSIER_REPORT))
                .thenReturn(List.of(first));
        when(templateService.save(isNull(), eq(JinjaTemplatesInstaller.REPORT_TEMPLATE_NAME), anyString(),
                eq(OaTemplateType.DOSSIER_REPORT), eq(false), eq(true), any()))
                        .thenReturn(OaTemplate.builder().id("bugfix").build());

        installer(Duration.ofSeconds(1)).install(OaTemplateType.DOSSIER_REPORT);

        // a new template, made the default; the first one is left as is
        verify(templateService).save(isNull(), eq(JinjaTemplatesInstaller.REPORT_TEMPLATE_NAME), anyString(),
                eq(OaTemplateType.DOSSIER_REPORT), eq(false), eq(true), any());
        verify(templateService, never()).setDefault("first");
        verify(templateService, never()).save(eq("first"), any(), any(), any(), eq(false), eq(true), any());
    }

    @Test
    void nothingIsInstalledWhenTheFileServiceNeverComesUp() throws Exception {
        when(uploadRoutesApi.pingWithHttpInfo()).thenThrow(new ApiException("connection refused"));

        assertThatThrownBy(() -> installer(Duration.ofMillis(20)).install()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file service");
        verify(templateService, never()).save(any(), any(), any(), any(), eq(false), eq(true), any());
    }

    @Test
    void rollbackMakesTheLatestV1TemplatesTheDefaultAgain() {
        var v1 = OaTemplate.builder().id("inv-v1").type(OaTemplateType.INVOICE).version(OaTemplateVersion.V1).build();
        var v2 = OaTemplate.builder().id("inv-v2").type(OaTemplateType.INVOICE).build();
        when(repository.findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(OaTemplateType.INVOICE))
                .thenReturn(List.of(v2, v1));

        installer(Duration.ZERO).uninstall();

        verify(templateService).setDefault("inv-v1");
        verify(templateService, never()).setDefault("inv-v2");
    }
}
