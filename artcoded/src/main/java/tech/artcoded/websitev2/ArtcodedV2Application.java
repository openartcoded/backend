package tech.artcoded.websitev2;

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.support.mongock.annotations.MongockSupport;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.rest.exception.DefaultExceptionHandler;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
// migrations: new changes are Flamingock @Change classes in the migrations package (named _NNNN__Description). The
// legacy Mongock @ChangeUnit classes (changelogs package) still run, after their mongockChangeLog history is imported
@EnableFlamingock(stages = @Stage(name = "artcoded", location = "tech.artcoded.websitev2.migrations"))
@MongockSupport(targetSystem = MigrationTargetSystem.ID,
        // collection holding the Mongock history
        origin = "${application.migrations.mongock-origin:mongockChangeLog}",
        // fresh database (new install, tests): nothing to import
        emptyOriginAllowed = "true",
        // changelogs deleted after they ran (add-thumb-for-each-upload, add-thumb-for-each-upload-fixed) are still in
        // the Mongock history: they are skipped instead of failing the import
        ignoreUnknownEntries = "${application.migrations.ignore-unknown-mongock-entries:true}")
@EnableConfigurationProperties
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
@Import(DefaultExceptionHandler.class)
public class ArtcodedV2Application implements AsyncConfigurer {

    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    public static void main(String[] args) {
        SpringApplication.run(ArtcodedV2Application.class, args);
    }
}
