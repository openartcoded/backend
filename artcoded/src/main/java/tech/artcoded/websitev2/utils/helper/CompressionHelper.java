package tech.artcoded.websitev2.utils.helper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

@Slf4j
public final class CompressionHelper {
  public record SourceType(File source, boolean walkDir) {
  }

  @SneakyThrows
  public static void zip(File zipFile, List<SourceType> sourceDirs) {
    try (var zip = new ZipFile(zipFile)) {
      for (var sourceDir : sourceDirs) {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setIncludeRootFolder(!sourceDir.walkDir);
        zip.addFolder(sourceDir.source, zipParameters);

      }

    }

  }

  @SneakyThrows
  public static void unzip(String archivePath, String unzipPath) {
    try (var zipFile = new ZipFile(archivePath)) {
      zipFile.extractAll(unzipPath);
    }
  }

  public static void tar(File tarFile, List<SourceType> sourceDirs) throws IOException {
    try (OutputStream fout = FileUtils.openOutputStream(tarFile);
        BufferedOutputStream buffOut = new BufferedOutputStream(fout);
        GzipCompressorOutputStream gzOut = new GzipCompressorOutputStream(buffOut);
        TarArchiveOutputStream tOut = new TarArchiveOutputStream(gzOut)) {

      for (var sourceDir : sourceDirs) {
        var source = sourceDir.source;

        if (!source.isDirectory()) {
          throw new RuntimeException("only directory for source");

        }
        if (sourceDir.walkDir) {
          for (var f : source.listFiles()) {
            addFileToTarGz(tOut, f.getAbsolutePath(), "");
          }
        } else {
          addFileToTarGz(tOut, source.getAbsolutePath(), "");
        }

      }
      tOut.finish();
    }
  }

  private static void addFileToTarGz(TarArchiveOutputStream tOut, String path, String base) throws IOException {
    File f = new File(path);
    String entryName = base + f.getName();
    log.info("TAR: add entry  {}", entryName);
    TarArchiveEntry tarEntry = new TarArchiveEntry(f, entryName);
    tOut.putArchiveEntry(tarEntry);
    if (f.isFile()) {
      IOUtils.copy(new FileInputStream(f), tOut);
      tOut.closeArchiveEntry();
    } else {
      tOut.closeArchiveEntry();
      File[] children = f.listFiles();
      if (children != null) {
        for (File child : children) {
          addFileToTarGz(tOut, child.getAbsolutePath(), entryName + "/");
        }
      }
    }
  }

  @SneakyThrows
  public static void untar(String sourceTar, String targetDir) {
    var source = Paths.get(sourceTar);
    var target = Paths.get(targetDir);
    try (InputStream fi = Files.newInputStream(source);
        BufferedInputStream bi = new BufferedInputStream(fi);
        GzipCompressorInputStream gzi = new GzipCompressorInputStream(bi);
        TarArchiveInputStream ti = new TarArchiveInputStream(gzi)) {

      ArchiveEntry entry;
      while ((entry = ti.getNextEntry()) != null) {

        // create a new path, zip slip validate
        Path newPath = zipSlipProtect(entry, target);

        if (entry.isDirectory()) {
          Files.createDirectories(newPath);
        } else {

          // check parent folder again
          Path parent = newPath.getParent();
          if (parent != null) {
            if (Files.notExists(parent)) {
              Files.createDirectories(parent);
            }
          }

          // copy TarArchiveInputStream to Path newPath
          Files.copy(ti, newPath, StandardCopyOption.REPLACE_EXISTING);

        }
      }
    }
  }

  private static Path zipSlipProtect(ArchiveEntry entry, Path targetDir)
      throws IOException {

    Path targetDirResolved = targetDir.resolve(entry.getName());

    // make sure normalized file still has targetDir as its prefix,
    // else throws exception
    Path normalizePath = targetDirResolved.normalize();

    if (!normalizePath.startsWith(targetDir)) {
      throw new IOException("Bad entry: " + entry.getName());
    }

    return normalizePath;
  }
}
