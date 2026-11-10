package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import tech.artcoded.websitev2.pages.dossier.report.DossierReportService.ReportFile;

class ReportCacheTest {
    @TempDir
    Path directory;

    private final AtomicInteger generated = new AtomicInteger();

    private ReportCache cache() {
        return new ReportCache(new ConcurrentMapCacheManager(), directory.toString(), Duration.ofDays(7));
    }

    private ReportFile generate(String content) {
        generated.incrementAndGet();
        return new ReportFile("report.pdf", content.getBytes(),
                DossierReport.builder().periodLabel("October 2026").insights(List.of()).build());
    }

    private long pdfs() throws Exception {
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.toString().endsWith(".pdf")).count();
        }
    }

    @Test
    void aReportIsGeneratedOnceThenReadFromItsFile() throws Exception {
        var cache = cache();
        var first = cache.get("annual-2025-2025-12", () -> generate("pdf 1"));
        var second = cache.get("annual-2025-2025-12", () -> generate("pdf 2"));

        assertThat(generated).hasValue(1);
        assertThat(new String(second.content())).isEqualTo("pdf 1");
        assertThat(second.fileName()).isEqualTo("report.pdf");
        assertThat(second.report().getPeriodLabel()).isEqualTo("October 2026");
        assertThat(first.content()).isEqualTo(second.content());
        assertThat(pdfs()).isEqualTo(1);
        // another key, another report
        cache.get("annual-2024-2024-12", () -> generate("pdf 3"));
        assertThat(generated).hasValue(2);
        assertThat(pdfs()).isEqualTo(2);
    }

    @Test
    void aChangeInvalidatesEveryReportAndDeletesTheFiles() throws Exception {
        var cache = cache();
        cache.get("a", () -> generate("a"));
        cache.get("b", () -> generate("b"));
        assertThat(pdfs()).isEqualTo(2);

        cache.invalidateAll("dossier saved");

        assertThat(pdfs()).isZero();
        assertThat(new String(cache.get("a", () -> generate("a again")).content())).isEqualTo("a again");
        assertThat(generated).hasValue(3);
    }

    @Test
    void anEvictedReportIsGeneratedAgain() throws Exception {
        var cache = cache();
        cache.get("a", () -> generate("a"));
        cache.get("b", () -> generate("b"));
        cache.evict("a");
        assertThat(pdfs()).isEqualTo(1);
        assertThat(new String(cache.get("a", () -> generate("a2")).content())).isEqualTo("a2");
        assertThat(new String(cache.get("b", () -> generate("b2")).content())).isEqualTo("b");
    }

    @Test
    void aMissingFileIsGeneratedAgain() throws Exception {
        var cache = cache();
        cache.get("a", () -> generate("a"));
        try (var files = Files.list(directory)) {
            files.forEach(p -> p.toFile().delete());
        }
        assertThat(new String(cache.get("a", () -> generate("a2")).content())).isEqualTo("a2");
        assertThat(generated).hasValue(2);
    }

    @Test
    void aReportGeneratedDuringAnInvalidationIsNotKept() throws Exception {
        var cache = cache();
        var report = cache.get("a", () -> {
            var file = generate("computed before the change");
            cache.invalidateAll("expense saved meanwhile");
            return file;
        });
        assertThat(new String(report.content())).isEqualTo("computed before the change");
        assertThat(pdfs()).isZero();
        cache.get("a", () -> generate("after"));
        assertThat(generated).hasValue(2);
    }

    @Test
    void concurrentRequestsGenerateOnce() throws Exception {
        var cache = cache();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = java.util.stream.IntStream.range(0, 8).mapToObj(i -> executor.submit(() -> {
                start.await();
                return cache.get("same", () -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return generate("once");
                });
            })).toList();
            start.countDown();
            for (var future : futures) {
                assertThat(new String(future.get().content())).isEqualTo("once");
            }
        }
        assertThat(generated).hasValue(1);
    }

    @Test
    void expiredFilesAreDeleted() throws Exception {
        var old = directory.resolve("annual-2020-old.pdf");
        Files.writeString(old, "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(8))));
        var recent = directory.resolve("annual-2021-recent.pdf");
        Files.writeString(recent, "recent");
        var other = directory.resolve("keep.txt");
        Files.writeString(other, "not a report");

        cache().sweep();

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(other).exists();
    }

    @Test
    void theCachedModelIsSerializable() throws Exception {
        // what redis stores: a real report, with charts, kpis and the forecast of an annual report
        var input = new DossierReportCalculator.Input(AnnualReportCalculatorTest.Q1, YearMonth.of(2025, 1),
                YearMonth.of(2025, 12), AnnualReportCalculatorTest.INVOICES, AnnualReportCalculatorTest.EXPENSES,
                List.of(), List.of(), LocalDate.of(2026, 1, 20));
        var report = AnnualReportCalculator.compute(input, List.of(AnnualReportCalculatorTest.Q1));
        var cached = new ReportCache.CachedReport("annual-2025", "/tmp/x.pdf", "annual-report-2025.pdf", report);

        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(cached);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var read = (ReportCache.CachedReport) in.readObject();
            assertThat(read.report()).isEqualTo(report);
            assertThat(read.report().getForecast().getChart().getBars()).isNotEmpty();
        }
    }
}
