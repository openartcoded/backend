package tech.artcoded.websitev2.shell;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
  public SshServer sshServer(REPLCommand command) throws IOException {
    SshServer sshd = SshServer.setUpDefaultServer();
    sshd.setPort(port);
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

    sshd.setPasswordAuthenticator((u, p, _) -> username.equals(u) && password.equals(p));

    sshd.setShellFactory((_) -> command);
    sshd.start();

    log.info("SSH shell started on port 2222 (user=admin, pass=secret)");
    return sshd;
  }

}
