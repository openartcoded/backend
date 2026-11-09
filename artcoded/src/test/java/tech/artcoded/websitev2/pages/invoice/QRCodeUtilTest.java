package tech.artcoded.websitev2.pages.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QRCodeUtilTest {
    // well known valid example IBANs
    private static final String BE_IBAN = "BE68 5390 0754 7034";
    private static final String DE_IBAN = "DE89 3704 0044 0532 0130 00";

    @Test
    void validIbansAreNormalized() {
        assertThat(QRCodeUtil.extractIBAN(BE_IBAN)).isEqualTo("BE68539007547034");
        assertThat(QRCodeUtil.extractIBAN("be68-5390-0754-7034")).isEqualTo("BE68539007547034");
        assertThat(QRCodeUtil.extractIBAN(DE_IBAN)).isEqualTo("DE89370400440532013000");
    }

    @Test
    void nullIbanIsRejected() {
        assertThatThrownBy(() -> QRCodeUtil.extractIBAN(null)).hasMessageContaining("iban is null");
    }

    @ParameterizedTest
    @ValueSource(strings = { "BE68 5390", "BE68539007547034BE68539007547034BE685" })
    void ibanWithWrongLengthIsRejected(String iban) {
        assertThatThrownBy(() -> QRCodeUtil.extractIBAN(iban)).hasMessageContaining("length");
    }

    @Test
    void ibanNotStartingWithCountryAndChecksumIsRejected() {
        assertThatThrownBy(() -> QRCodeUtil.extractIBAN("1268539007547034"))
                .hasMessageContaining("two letters + two checksum digits");
    }

    @Test
    void ibanWithWrongChecksumIsRejected() {
        assertThatThrownBy(() -> QRCodeUtil.extractIBAN("BE69 5390 0754 7034"))
                .hasMessageContaining("validation check failed");
    }

    @Test
    void generatesAReadablePngQrCode() throws Exception {
        var png = QRCodeUtil.generateBankQRCode(Optional.of("GKCCBEBB"), "Artcoded", BE_IBAN, "1210.00",
                "+++123/4567/89002+++");
        assertThat(png).isNotEmpty();
        // PNG signature
        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(image.getHeight()).isPositive();
        // white quiet zone around the code
        assertThat(image.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void generatesQrCodeWithoutBic() {
        assertThat(QRCodeUtil.generateBankQRCode(Optional.empty(), "Artcoded", BE_IBAN, "10.00", "ref")).isNotEmpty();
    }

    @Test
    void invalidIbanPreventsQrCodeGeneration() {
        assertThatThrownBy(() -> QRCodeUtil.generateBankQRCode(Optional.empty(), "x", "invalid", "1", "ref"))
                .isInstanceOf(RuntimeException.class);
    }
}
