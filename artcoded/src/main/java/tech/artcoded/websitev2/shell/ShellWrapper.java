package tech.artcoded.websitev2.shell;

import javax.inject.Inject;

import org.jline.terminal.Terminal;
import org.springframework.shell.ResultHandlerService;
import org.springframework.shell.Shell;
import org.springframework.shell.command.CommandCatalog;
import org.springframework.shell.context.ShellContext;
import org.springframework.shell.exit.ExitCodeMappings;
import org.springframework.stereotype.Component;

@Component
public class ShellWrapper extends Shell {

    @Inject
    public ShellWrapper(ResultHandlerService resultHandlerService, CommandCatalog commandRegistry, Terminal terminal,
            ShellContext shellContext, ExitCodeMappings exitCodeMappings) {
        super(resultHandlerService, commandRegistry, terminal, shellContext, exitCodeMappings);
    }

    public Object evaluateCommand(String command) {
        return super.evaluate(() -> command);
    }
}
