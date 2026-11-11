package tech.artcoded.websitev2.peppol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import lombok.SneakyThrows;

/**
 * Extra files of an invoice, embedded in its UBL (Peppol BIS 3) document next to the invoice PDF.
 * <p>
 * Done on the generated XML rather than in the templates: the Peppol templates are editable and stored in the database,
 * so this works with any template version.
 */
public final class PeppolAttachments {
    static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String ADDITIONAL_DOCUMENT_REFERENCE = "AdditionalDocumentReference";

    /** the mime codes Peppol BIS 3 accepts for an attachment (code list of rule BR-CL-24) */
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of("application/pdf", "image/png", "image/jpeg",
            "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet");

    /**
     * elements that come right after AdditionalDocumentReference in a UBL invoice / credit note: when the document has
     * no AdditionalDocumentReference yet, the attachments are inserted before the first of them
     */
    static final List<String> FOLLOWING_ELEMENTS = List.of("ProjectReference", "Signature", "AccountingSupplierParty");

    public record Attachment(String filename, String mimeCode, byte[] content) {
    }

    private PeppolAttachments() {
    }

    public static boolean isAllowed(String mimeCode) {
        return mimeCode != null && ALLOWED_MIME_TYPES.contains(mimeCode.toLowerCase());
    }

    /** the UBL document with one AdditionalDocumentReference per attachment, after the existing ones */
    @SneakyThrows
    public static byte[] addAttachments(byte[] ubl, List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return ubl;
        }
        for (var a : attachments) {
            if (!isAllowed(a.mimeCode())) {
                throw new IllegalArgumentException(
                        "'%s' (%s) cannot be attached to a Peppol document".formatted(a.filename(), a.mimeCode()));
            }
        }
        Document doc = parse(ubl);
        Element root = doc.getDocumentElement();
        String cac = Optional.ofNullable(root.lookupPrefix(CAC)).orElse("cac");
        String cbc = Optional.ofNullable(root.lookupPrefix(CBC)).orElse("cbc");

        Node before = insertionPoint(root);
        for (var a : attachments) {
            Element reference = doc.createElementNS(CAC, cac + ":" + ADDITIONAL_DOCUMENT_REFERENCE);
            Element id = doc.createElementNS(CBC, cbc + ":ID");
            id.setTextContent(a.filename());
            Element attachment = doc.createElementNS(CAC, cac + ":Attachment");
            Element binary = doc.createElementNS(CBC, cbc + ":EmbeddedDocumentBinaryObject");
            binary.setAttribute("mimeCode", a.mimeCode().toLowerCase());
            binary.setAttribute("filename", a.filename());
            binary.setTextContent(Base64.getEncoder().encodeToString(a.content()));
            attachment.appendChild(binary);
            reference.appendChild(id);
            reference.appendChild(attachment);
            // insertBefore(x, null) appends at the end
            root.insertBefore(reference, before);
        }
        return serialize(doc);
    }

    /** the node the new references go before: after the last existing reference, or before the next element */
    static Node insertionPoint(Element root) {
        Element lastReference = null;
        Element firstFollowing = null;
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && CAC.equals(e.getNamespaceURI())) {
                if (ADDITIONAL_DOCUMENT_REFERENCE.equals(e.getLocalName())) {
                    lastReference = e;
                } else if (firstFollowing == null && FOLLOWING_ELEMENTS.contains(e.getLocalName())) {
                    firstFollowing = e;
                }
            }
        }
        return lastReference != null ? lastReference.getNextSibling() : firstFollowing;
    }

    private static Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // the xml comes from our own templates, but never resolve external entities anyway
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static byte[] serialize(Document doc) throws Exception {
        var factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
        try (var out = new ByteArrayOutputStream()) {
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        }
    }
}
