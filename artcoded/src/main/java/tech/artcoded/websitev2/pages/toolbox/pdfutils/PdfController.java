package tech.artcoded.websitev2.pages.toolbox.pdfutils;

import static org.apache.commons.io.FileUtils.getTempDirectory;
import static tech.artcoded.websitev2.rest.util.RestUtil.transformToByteArrayResource;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

  @PostMapping(value = "/split", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ByteArrayResource> splitPdf(@RequestPart(value = "pdf") MultipartFile pdf) throws Exception {
    PDDocument document = PDDocument.load(pdf.getInputStream());
    File tempZip = new File(getTempDirectory(), pdf.getOriginalFilename() + ".zip");
    ZipParameters zipParameters = new ZipParameters();
    zipParameters.setIncludeRootFolder(false);
    List<File> files = new ArrayList<>();
    try (var zipFile = new ZipFile(tempZip)) {
      for (var page : document.getPages()) {
        try (var content = page.getContents()) {
          var temp = new File(getTempDirectory(), IdGenerators.get() + ".pdf");
          FileUtils.copyInputStreamToFile(content, temp);
          files.add(temp);
        }
      }
      zipFile.addFiles(files, zipParameters);
    }
    var zipBAR = transformToByteArrayResource(tempZip.getName(),
        URLConnection.guessContentTypeFromName(tempZip.getName()), FileUtils.readFileToByteArray(tempZip));
    FileUtils.delete(tempZip);
    files.forEach(File::delete);
    return zipBAR;

  }

}
