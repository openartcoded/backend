package tech.artcoded.websitev2.mongodb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import tech.artcoded.websitev2.mongodb.converters.DateToYearMonthConverter;
import tech.artcoded.websitev2.mongodb.converters.LocalDateToYearMonthConverter;
import tech.artcoded.websitev2.mongodb.converters.YearMonthToLocalDateConverter;

import javax.inject.Inject;
import java.util.List;

@Configuration
public class MongoConfig {
  @Bean
  @Inject
  public GridFsTemplate gridFsTemplate(MongoDatabaseFactory mongoDatabaseFactory,
                                       MappingMongoConverter mappingMongoConverter) {
    return new GridFsTemplate(mongoDatabaseFactory, mappingMongoConverter);
  }

  @Bean
  public MongoCustomConversions mongoCustomConversions() {

    return new MongoCustomConversions(
      List.of(
        new LocalDateToYearMonthConverter(),
        new DateToYearMonthConverter(),
        new YearMonthToLocalDateConverter()));
  }
}
