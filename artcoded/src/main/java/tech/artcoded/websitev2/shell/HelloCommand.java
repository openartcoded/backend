package tech.artcoded.websitev2.shell;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class HelloCommand {
  @ShellMethod("Say hello world")
  public String hello() {
    return "hello world";
  }
}
