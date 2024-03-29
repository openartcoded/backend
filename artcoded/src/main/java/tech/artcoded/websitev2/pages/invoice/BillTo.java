package tech.artcoded.websitev2.pages.invoice;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BillTo implements Serializable {
  private static final long serialVersionUID = 1L;

  private String vatNumber;
  private String address;
  private String city;
  private String clientName;
  private String emailAddress;

}
