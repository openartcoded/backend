package tech.artcoded.websitev2.shell;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ShellFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.Shell;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;

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
  public SshServer sshServer(ShellWrapper shell) throws IOException {
    SshServer sshd = SshServer.setUpDefaultServer();
    sshd.setPort(port);
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

    sshd.setPasswordAuthenticator((u, p, _) -> username.equals(u) && password.equals(p));

    sshd.setShellFactory(new SpringShellFactory(shell));
    sshd.start();

    log.info("SSH shell started on port 2222 (user=admin, pass=secret)");
    return sshd;
  }

  static class SpringShellFactory implements ShellFactory {
    private final Shell shell;

    SpringShellFactory(Shell shell) {
      this.shell = shell;
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
        public void start(ChannelSession channel, org.apache.sshd.server.Environment env) throws IOException {
          new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8),
                    true)) {
              pw.println("Connected to Spring Shell. Type commands, 'exit' to quit.");
              String line;
              while (running && (line = br.readLine()) != null) {
                if (line.equalsIgnoreCase("exit"))
                  break;
                String cmd = line;
                shell.run(() -> () -> cmd);
              }
            } catch (Exception e) {
              log.error("error", e);
              PrintWriter epw = new PrintWriter(new OutputStreamWriter(err, StandardCharsets.UTF_8),
                  true);
              epw.write(ExceptionUtils.getStackTrace(e));
            } finally {
              callback.onExit(0);
            }
          }, "artcoded-ssh").start();
        }

        @Override
        public void destroy(ChannelSession channel) {
          running = false;
        }
      };
    }

  }
}
