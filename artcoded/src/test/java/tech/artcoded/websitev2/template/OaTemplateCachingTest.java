package tech.artcoded.websitev2.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.openapitools.client.api.TemplateRoutesApi;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;

/** The default templates and the v1 sources are cached, and evicted when a template changes. */
class OaTemplateCachingTest {
    private final OaTemplateRepository repository = mock(OaTemplateRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);

    /** class proxies like the application (spring.aop.proxy-target-class): OaTemplateService implements ILinkable */
    @Configuration
    @EnableCaching(proxyTargetClass = true)
    static class Caching {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    private ApplicationContextRunner spring() {
        return new ApplicationContextRunner().withUserConfiguration(Caching.class)
                .withBean(OaTemplateRepository.class, () -> repository)
                .withBean(IFileUploadService.class, () -> uploads)
                .withBean(TemplateRoutesApi.class, () -> mock(TemplateRoutesApi.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class)).withBean(OaTemplateService.class);
    }

    private static final OaTemplate V1 = OaTemplate.builder().id("t1").name("invoice").type(OaTemplateType.INVOICE)
            .version(OaTemplateVersion.V1).templateUploadId("upl").defaultTemplate(true).build();

    @Test
    void theDefaultTemplateIsReadOnceUntilADefaultChanges() {
        when(repository.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType.INVOICE))
                .thenReturn(Optional.of(V1));
        when(repository.findById("t1")).thenReturn(Optional.of(V1));
        when(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.INVOICE)).thenReturn(List.of(V1));

        spring().run(ctx -> {
            var service = ctx.getBean(OaTemplateService.class);
            assertThat(service.getDefault(OaTemplateType.INVOICE)).contains(V1);
            assertThat(service.getDefault(OaTemplateType.INVOICE)).contains(V1);
            verify(repository, times(1))
                    .findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType.INVOICE);

            service.setDefault("t1");
            service.getDefault(OaTemplateType.INVOICE);
            verify(repository, times(2))
                    .findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType.INVOICE);
        });
    }

    @Test
    void aV1SourceIsDownloadedOnceUntilTheTemplateIsOverwritten() {
        var upload = FileUpload.builder().id("upl").build();
        when(uploads.findOneById("upl")).thenReturn(Optional.of(upload));
        when(uploads.uploadToInputStream(upload))
                .thenAnswer(a -> new ByteArrayInputStream("${invoice}".getBytes(StandardCharsets.UTF_8)));
        when(repository.findById("t1")).thenReturn(Optional.of(V1));
        when(repository.save(any(OaTemplate.class))).thenAnswer(a -> a.getArgument(0));

        spring().run(ctx -> {
            var service = ctx.getBean(OaTemplateService.class);
            assertThat(service.v1Source(V1)).isEqualTo("${invoice}");
            assertThat(service.v1Source(V1)).isEqualTo("${invoice}");
            verify(uploads, times(1)).uploadToInputStream(upload);

            // renamed: same content, but the cache entry of the template is evicted anyway
            service.save("t1", "renamed", null, OaTemplateType.INVOICE, true, false, null);
            service.v1Source(V1);
            verify(uploads, times(2)).uploadToInputStream(upload);
        });
    }
}
