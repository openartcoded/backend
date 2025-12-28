package tech.artcoded.websitev2.pages.client;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/billable-client")
public class BillableClientController {
    private final BillableClientService service;

    public BillableClientController(BillableClientService service) {
        this.service = service;
    }

    @GetMapping("/find-by-contract-status")
    public List<BillableClient> findByContractStatus(@RequestParam("contractStatus") ContractStatus contractStatus) {
        return service.findByContractStatus(contractStatus);
    }

    @GetMapping("/find-all")
    public List<BillableClient> findAll() {
        return service.findAll();
    }

    @PostMapping("/save")
    public BillableClient save(@RequestBody BillableClient client) {
        return service.save(client);
    }

    @DeleteMapping
    public void delete(@RequestParam("id") String id) {
        service.delete(id);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void upload(@RequestParam(value = "id", required = false) String id,
            @RequestPart(value = "document") MultipartFile document) {
        this.service.upload(MockMultipartFile.copy(document), id);
    }

    @DeleteMapping(value = "/upload")
    public void deleteUpload(@RequestParam(value = "id") String id, @RequestParam("uploadId") String uploadId) {
        this.service.deleteUpload(id, uploadId);
    }

}
