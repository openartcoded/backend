
package tech.artcoded.websitev2.misc;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Profile({ "dev", "prod" })
@Slf4j
public class ShutdownAction implements Action {
    public static final String ACTION_KEY = "SHUTDOWN_ACTION";
    private final ConfigurableApplicationContext cac;

    public ShutdownAction(ConfigurableApplicationContext cac) {
        this.cac = cac;
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {

            messages.add("application is about to stop...");
            return resultBuilder.finishedDate(new Date()).status(StatusType.UNKNOWN).messages(messages).build();
        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }
    }

    @Override
    public ActionMetadata getMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Shutdown Action")
                .description("An action to shutdown the app.").allowedParameters(List.of())
                .defaultCronValue("0 30 1 2,15 * ?").build();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }

    @Override
    public void callback() {
        Thread.ofVirtual().start(() -> {
            try {
                log.info("sleeping 2s before stopping app...");
                Thread.sleep(2000);
            } catch (Throwable e) {
                log.error("could not sleep", e);
            }
            int exitCode = SpringApplication.exit(cac, () -> 0);
            System.exit(exitCode);
        });
    }

}
