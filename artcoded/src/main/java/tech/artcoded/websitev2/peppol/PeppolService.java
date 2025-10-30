package tech.artcoded.websitev2.peppol;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.phive.api.execute.ValidationExecutionManager;
import com.helger.phive.api.executorset.IValidationExecutorSet;
import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.api.result.ValidationResultList;
import com.helger.phive.api.validity.IValidityDeterminator;
import com.helger.phive.peppol.PeppolValidation2025_05;
import com.helger.phive.xml.source.IValidationSourceXML;
import com.helger.phive.xml.source.ValidationSourceXML;
import com.helger.xml.serialize.read.DOMReader;

import lombok.Getter;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;

@Service
@Slf4j
public class PeppolService implements CommandLineRunner {

    private static final DVRCoordinate VID_OPENPEPPOL_CREDIT_NOTE_UBL_V3 = PeppolValidation2025_05.VID_OPENPEPPOL_CREDIT_NOTE_UBL_V3;
    private static final DVRCoordinate VID_OPENPEPPOL_INVOICE_UBL_V3 = PeppolValidation2025_05.VID_OPENPEPPOL_INVOICE_UBL_V3;

    @Value("${application.upload.peppolFTPUser}")
    private String peppolFTPUser;

    @Value("${application.upload.peppolFTP}")
    private String peppolFTPURI;

    @Value("${application.upload.peppolFTPHostKey}")
    private String pathToPeppolFTPHostKey;

    private final IFileUploadService uploadService;
    @Getter
    private final ValidationExecutorSetRegistry<IValidationSourceXML> registry;
    private final InvoiceGenerationRepository invoiceRepository;

    private final ProducerTemplate producerTemplate;

    public PeppolService(IFileUploadService uploadService, ValidationExecutorSetRegistry<IValidationSourceXML> registry,
            ProducerTemplate producerTemplate, InvoiceGenerationRepository invoiceRepository) {
        this.uploadService = uploadService;
        this.invoiceRepository = invoiceRepository;
        this.registry = registry;
        this.producerTemplate = producerTemplate;
    }

    @SneakyThrows
    public void addInvoice(InvoiceGeneration invoice) {
        log.info("receiving invoice {}. copying it to {} and set status to processing...", invoice.getId(),
                peppolFTPURI);

        var validation = this.validate(invoice);
        if (!validation.y().containsNoError()) {
            throw new RuntimeException("peppol validation errors:\n " + validation.y().toString());
        } else {
            log.debug("invoice ubl valid.");
        }
        String endpoint = String.format(
                "%s/invoices?username=%s&privateKeyFile=%s&strictHostKeyChecking=no&useUserKnownHostsFile=false&autoCreate=true",
                peppolFTPURI, peppolFTPUser, pathToPeppolFTPHostKey);
        var out = Files.readAllBytes(validation.x().toPath());
        producerTemplate.sendBodyAndHeader(endpoint, out, Exchange.FILE_NAME, "%s.xml".formatted(invoice.getId()));
        this.invoiceRepository.findById(invoice.getId())
                .map(i -> i.toBuilder().peppolStatus(PeppolStatus.PROCESSING).build())
                .ifPresent(invoiceRepository::save);
    }

    public record Tuple<X, Y>(X x, Y y) {
    }

    @SneakyThrows
    public Tuple<File, ValidationResultList> validate(InvoiceGeneration invoice) {
        var ubl = this.uploadService.findOneById(invoice.getInvoiceUBLId()).orElseThrow(
                () -> new RuntimeException("file with id %s not found".formatted(invoice.getInvoiceUBLId())));

        var file = this.uploadService.getFile(ubl);
        var validationResults = this.validateFromFile(file, invoice.isCreditNote());
        return new Tuple<>(file, validationResults);
    }

    public ValidationResultList validateFromString(String xmlContent, boolean creditNote) {
        IValidationSourceXML src = ValidationSourceXML.create("invoice.xml", DOMReader.readXMLDOM(xmlContent));
        var dvr = creditNote ? VID_OPENPEPPOL_CREDIT_NOTE_UBL_V3 : VID_OPENPEPPOL_INVOICE_UBL_V3;
        IValidationExecutorSet<IValidationSourceXML> ves = getRegistry().getOfID(dvr);
        final ValidationResultList aValidationResult = ValidationExecutionManager
                .executeValidation(IValidityDeterminator.createDefault(), ves, src);
        return aValidationResult;
    }

    public ValidationResultList validateFromBytes(byte[] xmlBytes, boolean creditNote) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        return validateFromString(xml, creditNote);
    }

    public ValidationResultList validateFromFile(File file, boolean creditNote) throws Exception {
        String xml = Files.readString(file.toPath());
        return validateFromString(xml, creditNote);
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("warming up peppol validation...");
        var invoiceExample = new ClassPathResource("peppol-invoice-example.xml");
        var creditNoteExample = new ClassPathResource("peppol-creditnote-example.xml");

        var result = this.validateFromString(invoiceExample.getContentAsString(StandardCharsets.UTF_8), false);
        log.info("result from invoice example validation: {}", result);
        result = this.validateFromString(creditNoteExample.getContentAsString(StandardCharsets.UTF_8), true);
        log.info("result from creditnote example validation: {}", result);
    }

}
