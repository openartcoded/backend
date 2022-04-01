package tech.artcoded.websitev2.camel.mail;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.mail.ContentTypeResolver;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import javax.activation.DataHandler;
import javax.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.apache.commons.io.IOUtils.toByteArray;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

public interface MailTransformer {
  ContentTypeResolver CONTENT_TYPE_RESOLVER = URLConnection::guessContentTypeFromName;

  static Mail transform(Exchange exchange) {
    Message in = exchange.getIn();
    Date date = in.getHeader("Date", Date.class);
    String subject = getSubject(in.getHeader("Subject", String.class));
    String body = in.getBody(String.class);
    AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
    List<MultipartFile> multipartFiles = new ArrayList<>();
    if (attachmentMessage != null && attachmentMessage.hasAttachments()) {
      Map<String, DataHandler> attachments = attachmentMessage.getAttachments();
      attachments.values()
                 .stream()
                 .map(dh -> {
                   String filename = dh.getName();
                   String contentType = CONTENT_TYPE_RESOLVER.resolveContentType(filename);
                   byte[] data = toSupplier(() -> toByteArray(toSupplier(dh::getInputStream).get())).get();
                   return MockMultipartFile.builder()
                                           .name(filename)
                                           .originalFilename(filename)
                                           .contentType(contentType)
                                           .bytes(data)
                                           .build();
                 }).forEach(multipartFiles::add);
    }
    return Mail.builder().subject(subject).date(date).attachments(multipartFiles).body(body).build();
  }

  private static String getSubject(String value) {
    if (value == null) {
      return "NO_SUBJECT";
    }
    else {
      try {
        return MimeUtility.decodeText(MimeUtility.unfold(value));
      }
      catch (UnsupportedEncodingException var3) {
        return value;
      }
    }
  }

}
