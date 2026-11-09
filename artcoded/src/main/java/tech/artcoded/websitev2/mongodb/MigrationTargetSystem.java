package tech.artcoded.websitev2.mongodb;

import java.util.function.Function;

import org.springframework.data.mongodb.core.MongoTemplate;

import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import io.flamingock.targetsystem.mongodb.springdata.MongoDBSpringDataTargetSystem;
import lombok.extern.slf4j.Slf4j;

/**
 * The MongoDB database the migrations (Flamingock changes and legacy Mongock change units) are applied to.
 * <p>
 * Flamingock wraps transactional changes in a MongoDB transaction, which needs a replica set: on a standalone mongod
 * the first migration would fail with "Transaction numbers are only allowed on a replica set member or mongos". Like
 * Mongock did with {@code mongock.transactional=false}, changes then run without transaction when
 * {@code application.migrations.transactional} (env {@code MONGO_TRANSACTION_ENABLED}) is false.
 */
@Slf4j
public class MigrationTargetSystem extends MongoDBSpringDataTargetSystem {
    public static final String ID = "artcoded-mongodb";

    /** Runs the change directly: no rollback of a failed change, as with Mongock in non-transactional mode. */
    static final TransactionWrapper NO_TRANSACTION = new TransactionWrapper() {
        @Override
        public <T> T wrapInTransaction(ExecutionRuntime runtime, Function<ExecutionRuntime, T> operation) {
            return operation.apply(runtime);
        }
    };

    private final boolean transactional;

    public MigrationTargetSystem(MongoTemplate mongoTemplate, boolean transactional) {
        super(ID, mongoTemplate);
        this.transactional = transactional;
        log.info("migrations target system '{}' created, transactions {}", ID, transactional ? "enabled" : "disabled");
    }

    public boolean isTransactional() {
        return transactional;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return transactional ? super.getTxWrapper() : NO_TRANSACTION;
    }
}
