package tech.artcoded.websitev2.pages.toolbox.xpathutils;

import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/toolbox/public/xpath")
public class XPathUtilsRestController {

  @PostMapping(value = "/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> modelConversion(
      @RequestParam("expression") String expression,
      @RequestParam("data") String dataToEvaluate) {
    try {
      DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = builderFactory.newDocumentBuilder();
      Document xmlDocument = builder.parse(IOUtils.toInputStream(dataToEvaluate, StandardCharsets.UTF_8));
      XPath xPath = XPathFactory.newInstance().newXPath();
      NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);
      return ResponseEntity.ok(IntStream.range(0, nodeList.getLength())
          .mapToObj(nodeList::item)
          .map(n -> "%s".formatted(n.getTextContent()))
          .collect(Collectors.joining(", ")));
    } catch (Exception e) {
      return ResponseEntity.unprocessableEntity().body(e.getMessage());
    }
  }

}
