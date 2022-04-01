package tech.artcoded.websitev2.pages.fee;

public enum Tag {
  INTERNET, GSM, OIL, LEASING, ACCOUNTING, OTHER, TIMESHEET;

  public static String label(Tag key) {
    return switch (key) {
      case OIL -> "gas";
      default -> key.name().toLowerCase();
    };
  }
}
