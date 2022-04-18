package tech.artcoded.websitev2.pages.finance;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;

import javax.inject.Inject;
import java.util.*;

@RestController
@RequestMapping("/api/finance/portfolio")
@Slf4j
public class PortfolioController {

  private final PortfolioRepository portfolioRepository;

  @Inject
  public PortfolioController(PortfolioRepository portfolioRepository) {
    this.portfolioRepository = portfolioRepository;
  }

  @DeleteMapping
  @SwaggerHeaderAuthentication
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    log.warn("portfolio {} will be really deleted", id);
    this.portfolioRepository.deleteById(id);
    return ResponseEntity.ok(Map.entry("message", "portfolio deleted"));
  }

  @PostMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<Portfolio> findAll() {
    return portfolioRepository.findAll();
  }

  @PostMapping("/find-by-id")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Portfolio> findById(@RequestParam("id") String id) {
    return portfolioRepository.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/update-ticks")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> updateTag(@RequestBody Set<Tick> ticks, @RequestParam("id") String portfolioId) {
    portfolioRepository.findById(portfolioId)
      .map(p -> p.toBuilder().ticks(ticks).updatedDate(new Date()).build())
      .ifPresent(portfolioRepository::save);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/save")
  @SwaggerHeaderAuthentication
  public Portfolio save(@RequestBody Portfolio portfolio) {
    Portfolio build = Optional.ofNullable(portfolio.getId())
      .flatMap(this.portfolioRepository::findById)
      .map(Portfolio::toBuilder)
      .orElseGet(portfolio::toBuilder)
      .name(portfolio.getName())
      .principal(portfolio.isPrincipal())
      .build();
    Portfolio portfolioSaved = portfolioRepository.save(build);
    if (portfolioSaved.isPrincipal()) {
      this.portfolioRepository.findByPrincipalIsTrue().stream().filter(p -> !p.getId().equals(portfolioSaved.getId())).map(
        p -> p.toBuilder().principal(false).build()
      ).forEach(portfolioRepository::save);
    }
    return portfolioSaved;
  }


}
