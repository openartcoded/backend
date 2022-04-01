package tech.artcoded.websitev2.pages.invoice;

import lombok.Getter;

public enum InvoicingType {
  DAYS("day"), HOURS("hour");
  @Getter
  private String label;

  InvoicingType(String v) {
    this.label = v;
  }
}
