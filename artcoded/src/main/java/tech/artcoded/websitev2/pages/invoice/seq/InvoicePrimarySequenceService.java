package tech.artcoded.websitev2.pages.invoice.seq;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class InvoicePrimarySequenceService {

  private static final String PRIMARY_SEQUENCE = "INVOICE_PRIMARY_SEQUENCE";

  private final MongoOperations mongoOperations;

  public InvoicePrimarySequenceService(final MongoOperations mongoOperations) {
    this.mongoOperations = mongoOperations;
  }

  public long getNextValueAndIncrementBy(long inc) {
    InvoicePrimarySequence primarySequence = mongoOperations.findAndModify(
        Query.query(Criteria.where("_id").is(PRIMARY_SEQUENCE)),
        new Update().inc("seq", 1),
        FindAndModifyOptions.options().returnNew(true),
        InvoicePrimarySequence.class);
    if (primarySequence == null) {
      primarySequence = new InvoicePrimarySequence();
      primarySequence.setId(PRIMARY_SEQUENCE);
      primarySequence.setSeq(inc);
      mongoOperations.insert(primarySequence);
    }
    return primarySequence.getSeq();
  }

  public Long getCurrent() {
    var seq = mongoOperations.findOne(
        Query.query(Criteria.where("_id").is(PRIMARY_SEQUENCE)), InvoicePrimarySequence.class);
    if (seq == null) {
      return null;
    }
    return seq.getSeq();
  }

  public void setValueTo(long number) {
    mongoOperations.updateFirst(
        Query.query(Criteria.where("_id").is(PRIMARY_SEQUENCE)),
        new Update().set("seq", number),
        InvoicePrimarySequence.class);
  }
}
