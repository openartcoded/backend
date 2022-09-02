package tech.artcoded.websitev2.pages.client;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BillableClientRepository extends MongoRepository<BillableClient, String> {

  List<BillableClient> findByContractStatus(ContractStatus status);

}
