package tech.artcoded.websitev2.shell;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.codehaus.plexus.util.ExceptionUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class REPLCommand implements Command, Runnable {
  private InputStream in;
  private OutputStream out;
  private OutputStream err;
  private ExitCallback onExit;

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
    this.onExit = callback;
  }

  @Override
  public void start(ChannelSession session, Environment env) throws IOException {
    Thread.startVirtualThread(this);
  }

  @Override
  public void run() {
    try (
        PrintWriter writer = new PrintWriter(out, true);
        PrintWriter errorWriter = new PrintWriter(err, true);
        Scanner scanner = new Scanner(in)) {

      writer.println("Welcome to the SSH REPL. Type 'exit' to quit.");
      try {
        while (scanner.hasNextLine()) {
          String line = scanner.nextLine().trim();
          if (line.isEmpty())
            continue;
          if ("exit".equalsIgnoreCase(line))
            break;

          String[] parts = line.split("\\s+");
          String cmd = parts[0].toLowerCase(Locale.ROOT);
          String[] args = Arrays.copyOfRange(parts, 1, parts.length);

          switch (cmd) {
            case "add":
              if (args.length == 2) {
                try {
                  int a = Integer.parseInt(args[0]);
                  int b = Integer.parseInt(args[1]);
                  writer.println("Result: " + (a + b));
                } catch (NumberFormatException e) {
                  writer.println("Usage: add <int> <int>");
                }
              } else {
                writer.println("Usage: add <int> <int>");
              }
              break;

            case "echo":
              writer.println(String.join(" ", args));
              break;

            case "time":
              writer.println(new Date());
              break;

            default:
              writer.println("Unknown command: " + cmd);
              break;
          }
        }

        writer.println("Goodbye.");
        if (onExit != null)
          onExit.onExit(0);
      } catch (Exception e) {
        errorWriter.write("oops, an error occurred\n %s".formatted(ExceptionUtils.getStackTrace(e)));
      }
    }
  }

  @Override
  public void destroy(ChannelSession channel) {
  }

}
