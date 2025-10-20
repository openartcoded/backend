package tech.artcoded.websitev2.pages.client;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface BillableClientRepository extends MongoRepository<BillableClient, String> {

    List<BillableClient> findByContractStatus(ContractStatus status);

    List<BillableClient> findByContractStatusAndStartDateIsBefore(ContractStatus contractStatus, Date date);

    List<BillableClient> findByContractStatusInAndEndDateIsBefore(List<ContractStatus> statuses, Date date);

    long countByContractStatusInAndEndDateIsBefore(List<ContractStatus> statuses, Date date);

    List<BillableClient> findByOrderByContractStatusAsc();

    List<BillableClient> findByOrderByContractStatusDesc();

}
