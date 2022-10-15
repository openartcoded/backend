package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BillTo {
  private String vatNumber;
  private String address;
  private String city;
  private String clientName;
  private String emailAddress;

}
