package tech.artcoded.websitev2.mongodb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import tech.artcoded.websitev2.mongodb.converters.DateToYearMonthConverter;
import tech.artcoded.websitev2.mongodb.converters.LocalDateToYearMonthConverter;
import tech.artcoded.websitev2.mongodb.converters.YearMonthToLocalDateConverter;

import java.util.List;

@Configuration
public class MongoConfig {
  @Bean
  public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Bean
  public MongoCustomConversions mongoCustomConversions() {

    return new MongoCustomConversions(List.of(new LocalDateToYearMonthConverter(), new DateToYearMonthConverter(), new YearMonthToLocalDateConverter()));
  }
}
