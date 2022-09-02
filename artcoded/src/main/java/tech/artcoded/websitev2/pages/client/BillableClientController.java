package tech.artcoded.websitev2.pages.client;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billable-client")
@Slf4j
public class BillableClientController {
  private final NotificationService notificationService;
  private final BillableClientRepository repository;

  @Inject
  public BillableClientController(
    NotificationService notificationService, BillableClientRepository repository) {
    this.notificationService = notificationService;
    this.repository = repository;
  }

  @GetMapping("/find-by-contract-status")
  public List<BillableClient> findByContractStatus(@RequestParam ContractStatus contractStatus) {
    return repository.findByContractStatus(contractStatus);
  }

  @GetMapping("/find-all")
  public List<BillableClient> findAll() {
    return repository.findAll();
  }


}
