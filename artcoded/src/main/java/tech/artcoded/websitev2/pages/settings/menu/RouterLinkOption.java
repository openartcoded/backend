package tech.artcoded.websitev2.pages.settings.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RouterLinkOption {
  @Builder.Default
  private boolean exact = true;
}
