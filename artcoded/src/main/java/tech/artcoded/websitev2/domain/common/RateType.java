package tech.artcoded.websitev2.domain.common;

import org.springframework.data.annotation.Transient;

import lombok.Getter;

public enum RateType {
    DAYS("day"), HOURS("hour");

    @Getter
    private String label;

    RateType(String v) {
        this.label = v;
    }

    @Transient
    public String getUBLRateType() {
        return switch (this) {
            case RateType.HOURS -> "HUR";
            case RateType.DAYS -> "DAY";
        };
    }
}
