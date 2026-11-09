package tech.artcoded.websitev2.mongodb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
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

        return new MongoCustomConversions(List.of(new LocalDateToYearMonthConverter(), new DateToYearMonthConverter(),
                new YearMonthToLocalDateConverter()));
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
