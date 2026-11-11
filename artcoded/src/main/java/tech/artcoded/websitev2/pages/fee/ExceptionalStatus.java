package tech.artcoded.websitev2.pages.fee;

/** What was decided about a big expense. No status: not reviewed (maybe detected as exceptional). */
public enum ExceptionalStatus {
    /** exceptional (taxes, a one-off purchase...): apart in the business plan */
    CONFIRMED,
    /** big, but a normal expense */
    DENIED
}
