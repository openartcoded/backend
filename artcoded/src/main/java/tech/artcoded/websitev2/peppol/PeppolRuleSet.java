package tech.artcoded.websitev2.peppol;

import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.peppol.PeppolValidation2026_05;
import com.helger.phive.xml.source.IValidationSourceXML;

/**
 * Single place defining which Peppol BIS rule set is used. The registry and the coordinates used to look up the
 * validation executor set MUST come from the same release: before, the registry was filled with the 2026.03 rules while
 * the lookup used the 2025.05 coordinates. Bumping phive-rules-peppol only requires changing this class.
 * <p>
 * Our invoices use the regular billing profile ({@code urn:fdc:peppol.eu:2017:poacc:billing:3.0}), NOT self-billing:
 * {@code PeppolValidation2026_03} only contains the self-billing rules and rejects them. 2026_05 is Peppol BIS Billing
 * 3.0.21 (May 2026 release, mandatory since 2026-08-17).
 */
public final class PeppolRuleSet {
    public static final DVRCoordinate INVOICE = PeppolValidation2026_05.VID_OPENPEPPOL_INVOICE_UBL_V3;
    public static final DVRCoordinate CREDIT_NOTE = PeppolValidation2026_05.VID_OPENPEPPOL_CREDIT_NOTE_UBL_V3;

    private PeppolRuleSet() {
    }

    public static DVRCoordinate coordinate(boolean creditNote) {
        return creditNote ? CREDIT_NOTE : INVOICE;
    }

    public static ValidationExecutorSetRegistry<IValidationSourceXML> newRegistry() {
        var registry = new ValidationExecutorSetRegistry<IValidationSourceXML>();
        PeppolValidation2026_05.init(registry);
        return registry;
    }
}
