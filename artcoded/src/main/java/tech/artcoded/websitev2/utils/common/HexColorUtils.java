package tech.artcoded.websitev2.utils.common;

import java.util.regex.Pattern;

public class HexColorUtils {

    private static final String HEX_WEBCOLOR_PATTERN = "^#([a-fA-F\\d]{6}|[a-fA-F\\d]{3})$";

    private static final Pattern pattern = Pattern.compile(HEX_WEBCOLOR_PATTERN);

    private HexColorUtils() {
    }

    public static boolean isValid(final String colorCode) {
        return colorCode != null && pattern.matcher(colorCode).matches();
    }

}
