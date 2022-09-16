package tech.artcoded.websitev2.pages.finance;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

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
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    log.warn("portfolio {} will be really deleted", id);
    this.portfolioRepository.deleteById(id);
    return ResponseEntity.ok(Map.entry("message", "portfolio deleted"));
  }

  @DeleteMapping("/tick")
  public ResponseEntity<Map.Entry<String, String>> deleteTickFromPortfolio(@RequestParam("id") String id, @RequestParam("symbol") String symbol) {
    log.warn("portfolio {} will be really deleted", id);
    this.portfolioRepository.findById(id).ifPresent(portfolio -> portfolioRepository.save(portfolio.toBuilder()
      .ticks(portfolio.getTicks().stream().filter(t -> t.getSymbol().equals(symbol))
        .collect(Collectors.toSet())).build()));
    return ResponseEntity.ok(Map.entry("message", "tick deleted"));
  }

  @PostMapping("/find-all")
  public List<Portfolio> findAll() {
    return portfolioRepository.findAll();
  }

  @PostMapping("/find-by-id")
  public ResponseEntity<Portfolio> findById(@RequestParam("id") String id) {
    return portfolioRepository.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/update-ticks")
  public ResponseEntity<Void> updateTag(@RequestBody Set<Tick> ticks, @RequestParam("id") String portfolioId) {
    portfolioRepository.findById(portfolioId)
      .map(p -> p.toBuilder().ticks(ticks).updatedDate(new Date()).build())
      .ifPresent(portfolioRepository::save);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/save")
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
