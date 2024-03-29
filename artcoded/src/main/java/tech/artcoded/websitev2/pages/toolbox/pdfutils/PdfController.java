package tech.artcoded.websitev2.pages.toolbox.pdfutils;

import static org.apache.commons.io.FileUtils.getTempDirectory;
import static tech.artcoded.websitev2.rest.util.RestUtil.transformToByteArrayResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jena.atlas.logging.Log;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@RestController
@RequestMapping("/api/pdf")
@Slf4j
public class PdfController {
  private final FileUploadService fileUploadService;

  public PdfController(FileUploadService fileUploadService) {
    this.fileUploadService = fileUploadService;
  }

  @PostMapping(value = "/split", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ByteArrayResource> splitPdf(@RequestPart(value = "pdf") MultipartFile pdf) throws Exception {

    File tempZip = new File(getTempDirectory(), pdf.getOriginalFilename() + ".zip");
    ZipParameters zipParameters = new ZipParameters();
    zipParameters.setIncludeRootFolder(false);
    List<File> files = new ArrayList<>();
    try (var zipFile = new ZipFile(tempZip);
        var document = Loader.loadPDF(new RandomAccessReadBuffer(pdf.getInputStream()))) {
      for (var page : document.getPages()) {
        var temp = new File(getTempDirectory(), IdGenerators.get() + ".pdf");
        try (PDDocument doc = new PDDocument()) {
          doc.addPage(page);
          doc.save(temp);
          files.add(temp);
        }
      }
      zipFile.addFiles(files, zipParameters);
    }
    var zipBAR = transformToByteArrayResource(
        tempZip.getName(),
        URLConnection.guessContentTypeFromName(tempZip.getName()),
        FileUtils.readFileToByteArray(tempZip));
    FileUtils.delete(tempZip);
    files.forEach(File::delete);
    return zipBAR;
  }

  @PostMapping(value = "/rotate")
  @SneakyThrows
  public void rotate(@RequestParam(value = "rotation", defaultValue = "180") Integer rotation,
      @RequestParam(value = "id", required = true) String id) {
    log.info("rotate file with id {}, rotation value: {}", id, rotation);
    var upload = fileUploadService.findOneById(id)
        .filter(u -> MediaType.APPLICATION_PDF.toString().equals(
            u.getContentType()))
        .orElseThrow(() -> new RuntimeException("file not found"));
    try (var stream = fileUploadService.uploadToInputStream(upload);
        PDDocument pdf = Loader.loadPDF(IOUtils.toByteArray(stream));
        var baos = new ByteArrayOutputStream();
        var newDoc = new PDDocument()) {
      for (var page : pdf.getPages()) {
        int currentRotation = page.getRotation();
        int newRotation = (currentRotation + rotation) % 360;
        page.setRotation(newRotation);
        newDoc.addPage(page);
      }
      newDoc.save(baos);
      try (var bis = new ByteArrayInputStream(baos.toByteArray())) {
        fileUploadService.upload(
            upload.toBuilder().updatedDate(new Date()).build(), bis,
            upload.isPublicResource());
      }
    }
  }
}
