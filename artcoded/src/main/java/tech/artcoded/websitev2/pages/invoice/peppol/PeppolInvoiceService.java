package tech.artcoded.websitev2.pages.invoice.peppol;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;

@Service
@Slf4j
public class PeppolInvoiceService {

  private final Configuration configuration;

  public PeppolInvoiceService(Configuration configuration) {
    this.configuration = configuration;
  }

  @SneakyThrows
  public String generatePeppolXML(PersonalInfo personalInfo, InvoiceGeneration invoice) {
    Template template = configuration.getTemplate("peppol-invoice.xml");
    String xml = FreeMarkerTemplateUtils.processTemplateIntoString(template,
        Map.of("invoice", invoice, "personalInfo", personalInfo));
    // todo validate xml
    return null;
  }
}
