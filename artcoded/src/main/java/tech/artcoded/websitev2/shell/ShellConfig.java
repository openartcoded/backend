package tech.artcoded.websitev2.shell;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ShellFactory;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.TerminalBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.ResultHandlerService;
import org.springframework.shell.Shell;
import org.springframework.shell.command.CommandCatalog;
import org.springframework.shell.context.ShellContext;
import org.springframework.shell.exit.ExitCodeMappings;
import org.springframework.shell.jline.InteractiveShellRunner.JLineInputProvider;
import org.springframework.shell.jline.NonInteractiveShellRunner;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.context.annotation.Bean;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Configuration
@Slf4j
public class ShellConfig {
  @Value("${application.shell.port}")
  private int port;

  @Value("${application.shell.username}")
  private String username;

  @Value("${application.shell.password}")
  private String password;

  @Bean
  public SshServer sshServer(ResultHandlerService resultHandlerService, CommandCatalog commandRegistry,
      ShellContext shellContext, ExitCodeMappings exitCodeMappings, PromptProvider promptProvider) throws IOException {
    SshServer sshd = SshServer.setUpDefaultServer();
    sshd.setPort(port);
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

    sshd.setPasswordAuthenticator((u, p, _) -> username.equals(u) && password.equals(p));

    sshd.setShellFactory(
        new SpringShellFactory(resultHandlerService, commandRegistry, shellContext, exitCodeMappings, promptProvider));
    sshd.start();

    log.info("SSH shell started on port 2222 (user=admin, pass=secret)");
    return sshd;
  }

  static class SpringShellFactory implements ShellFactory {
    private final ResultHandlerService resultHandlerService;
    private final CommandCatalog commandRegistry;
    private final ShellContext shellContext;
    private final PromptProvider promptProvider;
    private final ExitCodeMappings exitCodeMappings;

    SpringShellFactory(ResultHandlerService resultHandlerService, CommandCatalog commandRegistry,
        ShellContext shellContext, ExitCodeMappings exitCodeMappings, PromptProvider promptProvider) {
      this.resultHandlerService = resultHandlerService;
      this.commandRegistry = commandRegistry;
      this.shellContext = shellContext;
      this.exitCodeMappings = exitCodeMappings;
      this.promptProvider = promptProvider;
    }

    @Override
    public Command createShell(ChannelSession session) {
      return new Command() {
        private InputStream in;
        private OutputStream out;
        private OutputStream err;
        private ExitCallback callback;
        private volatile boolean running = true;

        @Override
        public void setInputStream(InputStream in) {
          this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
          this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
          this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
          this.callback = callback;
        }

        @Override
        @SneakyThrows
        public void start(ChannelSession channel, org.apache.sshd.server.Environment env) throws IOException {
          var terminal = TerminalBuilder.builder().streams(in, out).build();
          var shell = new Shell(resultHandlerService, commandRegistry, terminal, shellContext, exitCodeMappings);
          shell.run(new JLineInputProvider(new LineReaderImpl(terminal), promptProvider));
        }

        @Override
        public void destroy(ChannelSession channel) {
          running = false;
        }
      };
    }

  }
}
