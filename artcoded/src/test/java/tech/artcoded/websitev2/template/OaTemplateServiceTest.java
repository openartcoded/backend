package tech.artcoded.websitev2.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.client.api.TemplateRoutesApi;
import org.openapitools.client.model.Context;
import org.openapitools.client.model.TemplateType;
import org.openapitools.client.model.TemplateV2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.IFileUploadService;

class OaTemplateServiceTest {
    private final OaTemplateRepository repository = mock(OaTemplateRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);
    private final TemplateRoutesApi api = mock(TemplateRoutesApi.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final OaTemplateService service = new OaTemplateService(repository, uploads, api, mongo);

    @TempDir
    Path tmp;

    /** name and content of the files sent to the file service, read while they exist */
    private final List<String> sentFiles = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        when(repository.save(any(OaTemplate.class))).thenAnswer(a -> a.getArgument(0));
        when(uploads.getTempFolder()).thenReturn(tmp.toFile());
        when(uploads.normalizeFilename(anyString())).thenAnswer(a -> a.getArgument(0));
        when(api.upsert(anyString(), any(), any(), anyList(), any(), any())).thenAnswer(a -> {
            List<File> files = a.getArgument(3);
            for (var f : files) {
                sentFiles.add(f.getName() + ":" + Files.readString(f.toPath()));
            }
            String id = a.getArgument(4);
            return new TemplateV2().id(id != null ? id : "fs-new").fileId("fs-file");
        });
    }

    private MultipartFile file(String name, String content) {
        return MockMultipartFile.builder().name(name).originalFilename(name).contentType("text/html")
                .bytes(content.getBytes(StandardCharsets.UTF_8)).build();
    }

    @Test
    void aNewTemplateIsV2AndStoredInTheFileService() throws Exception {
        var saved = service.save(null, "Invoice 2026", "jinja", OaTemplateType.INVOICE, false, false,
                file("invoice.j2", "{{ invoice.total }}"));

        assertThat(saved.getVersion()).isEqualTo(OaTemplateVersion.V2);
        assertThat(saved.getType()).isEqualTo(OaTemplateType.INVOICE);
        assertThat(saved.getFileServiceTemplateId()).isEqualTo("fs-new");
        assertThat(saved.getTemplateUploadId()).isNull();
        verify(api).upsert(eq("Invoice 2026"), eq(TemplateType.HTML), eq(Context.INVOICE), anyList(), isNull(),
                eq("jinja"));
        // the file service checks the content type from the extension
        assertThat(sentFiles).containsExactly("invoice.html:{{ invoice.total }}");
        verify(uploads, never()).upload(any(MultipartFile.class), anyString(), anyBoolean());
        assertThat(tmp.toFile().list()).as("temporary files are removed").isEmpty();
    }

    @Test
    void peppolTemplatesAreXmlInTheFileService() throws Exception {
        service.save(null, "peppol", null, OaTemplateType.PEPPOL_CREDIT_NOTE, false, false, file("cn.xml", "<x/>"));

        verify(api).upsert(eq("peppol"), eq(TemplateType.XML), eq(Context.PEPPOL), anyList(), isNull(), isNull());
        assertThat(sentFiles).containsExactly("cn.xml:<x/>");
    }

    @Test
    void timesheetTemplatesCanBeV2() throws Exception {
        service.save(null, "ts", null, OaTemplateType.TIMESHEET, false, false, file("ts.html", "{{ timesheet.name }}"));

        verify(api).upsert(eq("ts"), eq(TemplateType.HTML), eq(Context.TIMESHEET), anyList(), isNull(), isNull());
    }

    @Test
    void aV1TemplateIsAnUploadCorrelatedWithTheTemplate() throws Exception {
        when(uploads.upload(any(MultipartFile.class), anyString(), eq(false))).thenReturn("upl-1");

        var saved = service.save(null, "legacy", null, OaTemplateType.TIMESHEET, true, false, file("ts.ftl", "${x}"));

        assertThat(saved.getVersion()).isEqualTo(OaTemplateVersion.V1);
        assertThat(saved.getTemplateUploadId()).isEqualTo("upl-1");
        verify(uploads).upload(any(MultipartFile.class), eq(saved.getId()), eq(false));
        verifyNoInteractions(api);
    }

    @Test
    void overwritingAV1KeepsTheIdAndRemovesThePreviousFile() {
        var existing = OaTemplate.builder().id("t1").name("old").type(OaTemplateType.INVOICE)
                .version(OaTemplateVersion.V1).templateUploadId("upl-old").defaultTemplate(true).build();
        when(repository.findById("t1")).thenReturn(Optional.of(existing));
        when(uploads.upload(any(MultipartFile.class), eq("t1"), eq(false))).thenReturn("upl-new");

        var saved = service.save("t1", "new", null, OaTemplateType.INVOICE, true, false, file("i.ftl", "${x}"));

        assertThat(saved.getId()).isEqualTo("t1");
        assertThat(saved.getName()).isEqualTo("new");
        assertThat(saved.getTemplateUploadId()).isEqualTo("upl-new");
        assertThat(saved.isDefaultTemplate()).isTrue();
        assertThat(saved.getUpdatedDate()).isNotNull();
        verify(uploads).delete("upl-old");
        verify(uploads, never()).delete("upl-new");
    }

    @Test
    void overwritingAV2KeepsItsFileServiceTemplate() throws Exception {
        var existing = OaTemplate.builder().id("t2").name("old").type(OaTemplateType.CV).fileServiceTemplateId("fs-2")
                .build();
        when(repository.findById("t2")).thenReturn(Optional.of(existing));

        var saved = service.save("t2", "cv", null, OaTemplateType.CV, false, false, file("cv.html", "<p/>"));

        verify(api).upsert(eq("cv"), eq(TemplateType.HTML), eq(Context.CV), anyList(), eq("fs-2"), isNull());
        assertThat(saved.getFileServiceTemplateId()).isEqualTo("fs-2");
        verify(api, never()).deleteTemplById(anyString());
    }

    @Test
    void switchingAV1ToV2RemovesTheFreemarkerFile() throws Exception {
        var existing = OaTemplate.builder().id("t1").name("old").type(OaTemplateType.INVOICE)
                .version(OaTemplateVersion.V1).templateUploadId("upl-old").build();
        when(repository.findById("t1")).thenReturn(Optional.of(existing));

        var saved = service.save("t1", "old", null, OaTemplateType.INVOICE, false, false, file("i.html", "{{ x }}"));

        assertThat(saved.getVersion()).isEqualTo(OaTemplateVersion.V2);
        assertThat(saved.getTemplateUploadId()).isNull();
        assertThat(saved.getFileServiceTemplateId()).isEqualTo("fs-new");
        verify(uploads).delete("upl-old");
    }

    @Test
    void switchingAV2ToV1RemovesTheFileServiceTemplate() throws Exception {
        var existing = OaTemplate.builder().id("t2").name("cv").type(OaTemplateType.CV).fileServiceTemplateId("fs-2")
                .build();
        when(repository.findById("t2")).thenReturn(Optional.of(existing));
        when(uploads.upload(any(MultipartFile.class), eq("t2"), eq(false))).thenReturn("upl-1");

        var saved = service.save("t2", "cv", null, OaTemplateType.CV, true, false, file("cv.ftl", "${cv}"));

        assertThat(saved.getFileServiceTemplateId()).isNull();
        assertThat(saved.getTemplateUploadId()).isEqualTo("upl-1");
        verify(api).deleteTemplById("fs-2");
    }

    @Test
    void renamingWithoutFileKeepsTheContent() {
        var existing = OaTemplate.builder().id("t1").name("old").type(OaTemplateType.INVOICE)
                .version(OaTemplateVersion.V1).templateUploadId("upl").build();
        when(repository.findById("t1")).thenReturn(Optional.of(existing));

        var saved = service.save("t1", "renamed", "d", OaTemplateType.INVOICE, true, false, null);

        assertThat(saved.getName()).isEqualTo("renamed");
        assertThat(saved.getTemplateUploadId()).isEqualTo("upl");
        verifyNoInteractions(api);
        verify(uploads, never()).delete(anyString());
    }

    @Test
    void invalidRequestsAreRejected() {
        var invoice = OaTemplate.builder().id("t1").name("i").type(OaTemplateType.INVOICE)
                .version(OaTemplateVersion.V1).build();
        when(repository.findById("t1")).thenReturn(Optional.of(invoice));
        when(repository.findById("deleted"))
                .thenReturn(Optional.of(invoice.toBuilder().id("deleted").logicalDelete(true).build()));

        assertThatThrownBy(() -> service.save("t1", "x", null, OaTemplateType.CV, true, false, file("a.ftl", "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
        assertThatThrownBy(() -> service.save("missing", "x", null, OaTemplateType.CV, true, false, file("a.ftl", "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.save("deleted", "x", null, OaTemplateType.INVOICE, true, false, file("a", "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.save(null, "x", null, OaTemplateType.INVOICE, false, false, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("file is required");
        // a rename cannot change the version: the content would not match it
        assertThatThrownBy(() -> service.save("t1", "x", null, OaTemplateType.INVOICE, false, false, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("file is required");
        assertThatThrownBy(() -> service.save(null, " ", null, OaTemplateType.INVOICE, true, false, file("a", "a")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        verify(repository, never()).save(any());
    }

    @Test
    void theDefaultIsUniquePerType() {
        var cv = OaTemplate.builder().id("cv").type(OaTemplateType.CV).build();
        var previousCv = OaTemplate.builder().id("previous").type(OaTemplateType.CV).defaultTemplate(true).build();
        when(repository.findById("cv")).thenReturn(Optional.of(cv));
        when(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.CV)).thenReturn(List.of(previousCv));

        assertThat(service.setDefault("cv")).hasValueSatisfying(t -> assertThat(t.isDefaultTemplate()).isTrue());

        verify(repository).findByTypeAndDefaultTemplateIsTrue(OaTemplateType.CV);
        verify(repository).save(org.mockito.ArgumentMatchers
                .argThat(t -> t.getId().equals("previous") && !t.isDefaultTemplate()));
    }

    @Test
    void deletingAnUnusedV2RemovesItFromTheFileService() throws Exception {
        when(repository.findById("t2")).thenReturn(Optional.of(
                OaTemplate.builder().id("t2").type(OaTemplateType.CV).fileServiceTemplateId("fs-2").build()));

        service.delete("t2");

        verify(api).deleteTemplById("fs-2");
        verify(repository).deleteById("t2");
    }

    @Test
    void theFirstTemplateOfATypeIsItsDefault() throws Exception {
        when(uploads.upload(any(MultipartFile.class), anyString(), eq(false))).thenReturn("upl-1");

        var saved = service.save(null, "first", null, OaTemplateType.SMS, true, false, file("sms.ftl", "${x}"));

        assertThat(saved.isDefaultTemplate()).isTrue();
    }

    @Test
    void aNewTemplateIsNotTheDefaultUnlessAsked() throws Exception {
        var current = OaTemplate.builder().id("current").type(OaTemplateType.EMAIL).defaultTemplate(true).build();
        when(repository.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType.EMAIL))
                .thenReturn(Optional.of(current));
        when(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.EMAIL)).thenReturn(List.of(current));

        var notDefault = service.save(null, "v2", null, OaTemplateType.EMAIL, false, false,
                file("email.html", "{{ htmlBody }}"));
        assertThat(notDefault.isDefaultTemplate()).isFalse();
        verify(repository, never()).save(org.mockito.ArgumentMatchers.argThat(t -> t.getId().equals("current")));

        var newDefault = service.save(null, "v2 default", null, OaTemplateType.EMAIL, false, true,
                file("email.html", "{{ htmlBody }}"));
        assertThat(newDefault.isDefaultTemplate()).isTrue();
        verify(repository).save(org.mockito.ArgumentMatchers
                .argThat(t -> t.getId().equals("current") && !t.isDefaultTemplate()));
        // a text template is uploaded with the extension giving the content type of its rendering
        verify(api, org.mockito.Mockito.times(2)).upsert(anyString(), eq(TemplateType.TEXT), eq(Context.EMAIL),
                anyList(), isNull(), isNull());
        assertThat(sentFiles).containsOnly("email.html:{{ htmlBody }}");
    }

    @Test
    void theDefaultTemplateCannotBeDeleted() {
        when(repository.findById("d")).thenReturn(Optional.of(
                OaTemplate.builder().id("d").name("d").type(OaTemplateType.CV).defaultTemplate(true).build()));

        assertThatThrownBy(() -> service.delete("d")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default");
        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void aDeletedTemplateCannotBecomeTheDefault() {
        when(repository.findById("gone")).thenReturn(Optional.of(
                OaTemplate.builder().id("gone").type(OaTemplateType.CV).logicalDelete(true).build()));

        assertThat(service.setDefault("gone")).isEmpty();
        assertThat(service.setDefault("missing")).isEmpty();
        verify(repository, never()).save(any());
    }
}
