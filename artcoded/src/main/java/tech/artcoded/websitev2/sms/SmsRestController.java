package tech.artcoded.websitev2.sms;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sms")
public class SmsRestController {
  private final SmsService smsService;

  public SmsRestController(SmsService smsService) {
    this.smsService = smsService;
  }

  @PostMapping
  public void send(@RequestBody Sms sms) {
    // todo probably add some validations here
    smsService.send(sms);
  }
}
