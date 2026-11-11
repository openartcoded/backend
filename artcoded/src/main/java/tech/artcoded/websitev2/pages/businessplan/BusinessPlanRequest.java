package tech.artcoded.websitev2.pages.businessplan;

import java.math.BigDecimal;

/**
 * What the business plan is generated with. Everything is optional but the language.
 *
 * @param language
 *            EN, FR or NL
 * @param activity
 *            a short description of the activity (printed as is)
 * @param loanAmount
 *            the amount asked to the bank
 * @param loanMonths
 *            duration of the loan
 * @param loanRate
 *            yearly interest rate, in percent (e.g. 4.5)
 * @param loanPurpose
 *            what the loan is for
 */
public record BusinessPlanRequest(Language language, String activity, BigDecimal loanAmount, Integer loanMonths,
        BigDecimal loanRate, String loanPurpose) {

    public enum Language {
        EN, FR, NL
    }

    public Language languageOrDefault() {
        return language == null ? Language.EN : language;
    }

    public boolean hasLoan() {
        return loanAmount != null && loanAmount.signum() > 0 && loanMonths != null && loanMonths > 0;
    }

    /** part of the cache key: two requests with the same values give the same pdf */
    public String cacheKey() {
        return Integer.toHexString(java.util.Objects.hash(languageOrDefault(), activity,
                loanAmount == null ? null : loanAmount.stripTrailingZeros(), loanMonths,
                loanRate == null ? null : loanRate.stripTrailingZeros(), loanPurpose));
    }
}
