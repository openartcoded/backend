package tech.artcoded.websitev2.pages.personal;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Accountant implements Serializable {
  private static final long serialVersionUID = 1L;
  private String firstName;
  private String lastName;
  private String email;
}
