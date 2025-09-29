package tech.artcoded.websitev2.pages.invoice;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import io.nayuki.qrcodegen.QrCode;
import io.nayuki.qrcodegen.QrSegment;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;

@Slf4j
public class QRCodeUtil {

  @SneakyThrows
  public static byte[] generateBankQRCode(
      Optional<String> bic,
      String name,
      String iban,
      String amount,
      String remittance

  ) {
    var payload = """
        BCD
        001
        1
        SCT
        %s
        %s
        %s
        %s
        %s
        """.formatted(bic.orElse(""), name, extractIBAN(iban), amount, remittance).trim();
    QrCode qr = QrCode.encodeSegments(
        List.of(QrSegment.makeBytes(payload.getBytes())),
        QrCode.Ecc.MEDIUM);
    BufferedImage img = toImage(qr, 4, 0); // scale=10, border=4 modules
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(img, "png", baos);
      return baos.toByteArray();
    } catch (Exception e) {
      log.error("error occurred", e);
      throw new RuntimeException(e);
    }
  }

  private static BufferedImage toImage(QrCode qr, int scale, int border) {
    int size = qr.size + border * 2;
    BufferedImage img = new BufferedImage(size * scale, size * scale,
        BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();

    g.setColor(Color.WHITE);
    g.fillRect(0, 0, img.getWidth(), img.getHeight());
    g.setColor(Color.BLACK);

    for (int y = 0; y < qr.size; y++) {
      for (int x = 0; x < qr.size; x++) {
        if (qr.getModule(x, y)) {
          int px = (x + border) * scale;
          int py = (y + border) * scale;
          g.fillRect(px, py, scale, scale);
        }
      }
    }
    g.dispose();
    return img;
  }

  public static String extractIBAN(String iban) {
    if (iban == null)
      throw new RuntimeException("iban is null");

    String cleanIban = iban.replaceAll("[^A-Za-z0-9]", "").toUpperCase();

    if (cleanIban.length() < 15 || cleanIban.length() > 34) {
      throw new RuntimeException("iban length not between 15 && 34 ->" + iban);
    }

    if (!cleanIban.matches("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$")) {
      throw new RuntimeException("iban must start with two letters + two checksum digits ->" + iban);
    }

    String rearranged = cleanIban.substring(4) + cleanIban.substring(0, 4);

    // Convert letters to numbers: A=10, B=11, ..., Z=35
    StringBuilder numeric = new StringBuilder();
    for (char c : rearranged.toCharArray()) {
      if (Character.isDigit(c)) {
        numeric.append(c);
      } else if (Character.isLetter(c)) {
        numeric.append((c - 'A') + 10);
      } else {
        throw new RuntimeException("iban conversion: invalid letter ->" + iban);
      }
    }

    // BigInteger mod 97 check
    BigInteger num = new BigInteger(numeric.toString());
    if (!(num.mod(BigInteger.valueOf(97)).intValue() == 1)) {
      throw new RuntimeException("iban validation check failed ->" + iban);
    }
    return cleanIban;
  }
}
