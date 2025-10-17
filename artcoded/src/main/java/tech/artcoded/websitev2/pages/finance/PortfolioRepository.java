package tech.artcoded.websitev2.pages.finance;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PortfolioRepository extends MongoRepository<Portfolio, String> {
    List<Portfolio> findByPrincipalIsTrue();
}
