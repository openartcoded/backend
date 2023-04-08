package tech.artcoded.websitev2.pages.personal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Accountant {
  private String firstName;
  private String lastName;
  private String email;
}
