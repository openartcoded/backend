package tech.artcoded.websitev2.domain.common;

import lombok.Getter;

public enum RateType {
  DAYS("day"), HOURS("hour");
  @Getter
  private String label;

  RateType(String v) {
    this.label = v;
  }
}
