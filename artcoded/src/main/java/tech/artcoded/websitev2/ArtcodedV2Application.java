package tech.artcoded.websitev2;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.artcoded.websitev2.rest.exception.DefaultExceptionHandler;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
@EnableMongock
@Import(DefaultExceptionHandler.class)
public class ArtcodedV2Application implements AsyncConfigurer {

  @Override
  public Executor getAsyncExecutor() {
    return Executors.newCachedThreadPool();
  }

  public static void main(String[] args) {
    SpringApplication.run(ArtcodedV2Application.class, args);
  }
}
