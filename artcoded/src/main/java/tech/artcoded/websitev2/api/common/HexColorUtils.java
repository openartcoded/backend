package tech.artcoded.websitev2.api.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HexColorUtils {

  private static final String HEX_WEBCOLOR_PATTERN
          = "^#([a-fA-F\\d]{6}|[a-fA-F\\d]{3})$";

  private static final Pattern pattern = Pattern.compile(HEX_WEBCOLOR_PATTERN);

  public static boolean isValid(final String colorCode) {
    Matcher matcher = pattern.matcher(colorCode);
    return matcher.matches();
  }

}
