package tech.artcoded.websitev2.utils.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.artcoded.websitev2.utils.helper.CompressionHelper.SourceType;

class CompressionHelperTest {
    @TempDir
    Path tmp;

    private Path sampleTree() throws IOException {
        var root = Files.createDirectories(tmp.resolve("source"));
        Files.writeString(root.resolve("a.txt"), "alpha", StandardCharsets.UTF_8);
        var sub = Files.createDirectories(root.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "béta", StandardCharsets.UTF_8);
        return root;
    }

    @Test
    void tarGzRoundTripWalkingTheDirectory() throws Exception {
        var source = sampleTree();
        var archive = tmp.resolve("backup.tar.gz").toFile();

        CompressionHelper.tar(archive, List.of(new SourceType(source.toFile(), true)));
        var target = tmp.resolve("restored");
        CompressionHelper.untar(archive.getAbsolutePath(), target.toString());

        assertThat(target.resolve("a.txt")).hasContent("alpha");
        assertThat(Files.readString(target.resolve("sub/b.txt"), StandardCharsets.UTF_8)).isEqualTo("béta");
    }

    @Test
    void tarGzKeepsRootFolderWhenNotWalking() throws Exception {
        var source = sampleTree();
        var archive = tmp.resolve("backup.tar.gz").toFile();

        CompressionHelper.tar(archive, List.of(new SourceType(source.toFile(), false)));
        var target = tmp.resolve("restored");
        CompressionHelper.untar(archive.getAbsolutePath(), target.toString());

        assertThat(target.resolve("source/a.txt")).hasContent("alpha");
        assertThat(target.resolve("source/sub/b.txt")).exists();
    }

    @Test
    void untarToARelativeTargetWorks() throws Exception {
        var source = sampleTree();
        var archive = tmp.resolve("backup.tar.gz").toFile();
        CompressionHelper.tar(archive, List.of(new SourceType(source.toFile(), true)));
        var relative = Path.of("").toAbsolutePath().relativize(tmp.resolve("relative-target"));

        CompressionHelper.untar(archive.getAbsolutePath(), relative.toString());

        assertThat(tmp.resolve("relative-target/a.txt")).hasContent("alpha");
    }

    @Test
    void tarOnlyAcceptsDirectories() throws Exception {
        var file = Files.writeString(tmp.resolve("file.txt"), "x");
        assertThatThrownBy(() -> CompressionHelper.tar(tmp.resolve("x.tar.gz").toFile(),
                List.of(new SourceType(file.toFile(), false)))).hasMessageContaining("only directory");
    }

    @Test
    void untarRejectsZipSlipEntries() throws Exception {
        var archive = tmp.resolve("evil.tar.gz");
        try (var out = new TarArchiveOutputStream(
                new GzipCompressorOutputStream(new BufferedOutputStream(Files.newOutputStream(archive))))) {
            var content = "pwned".getBytes(StandardCharsets.UTF_8);
            var entry = new TarArchiveEntry("../../evil.txt");
            entry.setSize(content.length);
            out.putArchiveEntry(entry);
            out.write(content);
            out.closeArchiveEntry();
            out.finish();
        }
        var target = tmp.resolve("target");

        assertThatThrownBy(() -> CompressionHelper.untar(archive.toString(), target.toString()))
                .isInstanceOf(IOException.class).hasMessageContaining("Bad entry");
        assertThat(tmp.resolve("evil.txt")).doesNotExist();
        assertThat(tmp.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    void zipRoundTrip() throws Exception {
        var source = sampleTree();
        var zip = tmp.resolve("backup.zip").toFile();

        CompressionHelper.zip(zip, List.of(new SourceType(source.toFile(), false)));
        var target = tmp.resolve("unzipped");
        CompressionHelper.unzip(zip.getAbsolutePath(), target.toString());

        assertThat(target.resolve("source/a.txt")).hasContent("alpha");
        assertThat(Files.readString(target.resolve("source/sub/b.txt"), StandardCharsets.UTF_8)).isEqualTo("béta");
    }

    @Test
    void zipWithoutRootFolder() throws Exception {
        var source = sampleTree();
        var zip = tmp.resolve("backup.zip").toFile();

        CompressionHelper.zip(zip, List.of(new SourceType(source.toFile(), true)));
        var target = tmp.resolve("unzipped");
        CompressionHelper.unzip(zip.getAbsolutePath(), target.toString());

        assertThat(target.resolve("a.txt")).hasContent("alpha");
        assertThat(target.resolve("sub/b.txt")).exists();
    }
}
