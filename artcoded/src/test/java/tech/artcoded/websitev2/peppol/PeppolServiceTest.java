package tech.artcoded.websitev2.peppol;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;

import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.peppol.PeppolValidation2025_05;
import com.helger.phive.xml.source.IValidationSourceXML;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Slf4j
class PeppolServiceTest {

  @Mock
  private PeppolService peppolService;

  @BeforeEach
  public void setup() {
    final ValidationExecutorSetRegistry<IValidationSourceXML> registry = new ValidationExecutorSetRegistry<>();
    PeppolValidation2025_05.init(registry);

    Mockito.when(peppolService.validateFromString(anyString(), anyBoolean()))
        .thenCallRealMethod();
    Mockito.when(peppolService.getRegistry()).thenReturn(registry);
  }

  @Test
  @DisplayName("PeppolService::Validate from string test")
  public void validateFromString() throws Exception {
    log.info("warming up peppol validation...");
    var invoiceExample = new ClassPathResource("peppol-invoice-example.xml");
    var creditNoteExample = new ClassPathResource("peppol-creditnote-example.xml");

    var result = peppolService.validateFromString(invoiceExample.getContentAsString(StandardCharsets.UTF_8), false);
    log.info("result from invoice example validation: {}", result);
    assertTrue(result.containsNoError());
    result = peppolService.validateFromString(creditNoteExample.getContentAsString(StandardCharsets.UTF_8), true);
    log.info("result from creditnote example validation: {}", result);

    assertTrue(result.containsNoError());

  }

}
