package tech.artcoded.websitev2;

import io.mongock.runner.springboot.EnableMongock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.artcoded.websitev2.rest.exception.DefaultExceptionHandler;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
@EnableMongock
@EnableConfigurationProperties
@Import(DefaultExceptionHandler.class)
public class ArtcodedV2Application implements AsyncConfigurer {

  @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
  public AsyncTaskExecutor asyncTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
  }

  @Bean
  public UndertowDeploymentInfoCustomizer undertowDeploymentInfoCustomizer() {
    return deploymentInfo -> deploymentInfo.setExecutor(
        Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public Executor getAsyncExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
  }

  public static void main(String[] args) {
    SpringApplication.run(ArtcodedV2Application.class, args);
  }
}
