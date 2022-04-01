package tech.artcoded.websitev2.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ByteArrayContainer implements Serializable {
  private byte[] data;

}
