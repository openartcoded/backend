package tech.artcoded.websitev2.pages.fee.camel;

import lombok.Setter;
import org.apache.camel.component.mail.SearchTermBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.mail.search.SearchTerm;
import java.util.List;

@Configuration
@ConfigurationProperties("search-term")
public class FeeConfig {
  @Setter
  private List<String> froms;

  @Bean
  public SearchTerm searchTerm() {
    SearchTermBuilder builder = new SearchTermBuilder();

    froms.stream().map(f -> new SearchTermBuilder().from(f).build())
        .forEach(builder::or);

    return builder.unseen().build();
  }
}
