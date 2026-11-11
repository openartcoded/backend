package tech.artcoded.websitev2.pages.businessplan;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tech.artcoded.websitev2.rest.util.RestUtil;

@RestController
@RequestMapping("/api/business-plan")
public class BusinessPlanController {
    private final BusinessPlanService service;

    public BusinessPlanController(BusinessPlanService service) {
        this.service = service;
    }

    /**
     * The business plan (pdf) in english, french or dutch, generated on the fly from the invoices and the expenses
     * (cached until one of them changes).
     */
    @PostMapping("/generate")
    public ResponseEntity<ByteArrayResource> generate(@RequestBody BusinessPlanRequest request,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        if (request.loanMonths() != null && (request.loanMonths() < 0 || request.loanMonths() > 600)) {
            return ResponseEntity.badRequest().build();
        }
        var plan = service.generate(request, refresh);
        return RestUtil.transformToByteArrayResource(plan.fileName(), MediaType.APPLICATION_PDF_VALUE, plan.content());
    }
}
