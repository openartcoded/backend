package tech.artcoded.websitev2.pages.invoice;

import org.springframework.data.mongodb.repository.MongoRepository;
@Deprecated
public interface CurrentBillToRepository extends MongoRepository<CurrentBillTo, String> {

  default CurrentBillTo getOrDefault() {
    return this.findAll()
      .stream()
      .findFirst()
      .orElseGet(() -> CurrentBillTo.builder().billTo(BillTo.builder().build()).build());
  }
}
