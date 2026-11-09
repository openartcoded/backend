package tech.artcoded.websitev2.mongodb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions.BigDecimalRepresentation;

import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import tech.artcoded.websitev2.mongodb.converters.DateToYearMonthConverter;
import tech.artcoded.websitev2.mongodb.converters.LocalDateToYearMonthConverter;
import tech.artcoded.websitev2.mongodb.converters.YearMonthToLocalDateConverter;


@Configuration
public class MongoConfig {
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }

    /**
     * Spring Data MongoDB 5 has no default representation for BigDecimal/BigInteger anymore (it used to be String). The
     * amounts are stored as strings, so that representation is kept: without it they would no longer be converted.
     */
    @Bean
    @SuppressWarnings("deprecation") // STRING is deprecated (not for removal) in favor of DECIMAL128
    public MongoCustomConversions mongoCustomConversions() {
        return MongoCustomConversions.create(adapter -> {
            adapter.registerConverter(new LocalDateToYearMonthConverter());
            adapter.registerConverter(new DateToYearMonthConverter());
            adapter.registerConverter(new YearMonthToLocalDateConverter());
            adapter.bigDecimal(BigDecimalRepresentation.STRING);
        });
    }

    /** Database the migrations are applied to (picked up by the Flamingock auto-configuration). */
    @Bean
    public MigrationTargetSystem migrationTargetSystem(MongoTemplate mongoTemplate,
            @Value("${application.migrations.transactional:false}") boolean transactional) {
        return new MigrationTargetSystem(mongoTemplate, transactional);
    }

    /**
     * Where Flamingock records the applied migrations (collection flamingockAuditLog, plus a lock collection), in the
     * same database. The previous Mongock history (mongockChangeLog) is imported once at startup and left untouched.
     */
    @Bean
    public MongoDBSyncAuditStore migrationAuditStore(MigrationTargetSystem migrationTargetSystem) {
        return MongoDBSyncAuditStore.from(migrationTargetSystem);
    }
}
