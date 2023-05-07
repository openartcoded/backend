package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Person implements Serializable {
  private static final long serialVersionUID = 1L;
  private String firstname;
  private String lastname;
  private String title;
  private String phoneNumber;
  private Date birthdate;
  private String emailAddress;
  private String linkedinUrl;
  private String githubUrl;
  private String address;
  private String website;
}
