package tech.artcoded.websitev2.shell;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.codehaus.plexus.util.ExceptionUtils;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskService;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.io.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class REPLCommand implements Command, Runnable {
  private static final Pattern PATTERN = Pattern.compile("(\\d+)([smhd])");

  private InputStream in;
  private OutputStream out;
  private OutputStream err;
  private ExitCallback onExit;
  private final ReminderTaskService reminderTaskService;

  public REPLCommand(ReminderTaskService reminderTaskService) {
    super();
    this.reminderTaskService = reminderTaskService;
  }

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

  Duration parseDuration(String input) {
    var m = PATTERN.matcher(input.trim().toLowerCase());
    if (!m.matches()) {
      return Duration.ofHours(2);
    }

    long value = Long.parseLong(m.group(1));
    String unit = m.group(2);

    switch (unit) {
      case "s":
        return Duration.ofSeconds(value);
      case "m":
        return Duration.ofMinutes(value);
      case "h":
        return Duration.ofHours(value);
      case "d":
        return Duration.ofDays(value);
      default:
        throw new IllegalArgumentException("Unknown unit: " + unit);
    }
  }

  @Override
  @SneakyThrows
  public void run() {
    try (
        PrintWriter writer = new PrintWriter(out, true);
        PrintWriter errorWriter = new PrintWriter(err, true);
        var ir = new InputStreamReader(in);
        BufferedReader reader = new BufferedReader(ir)) {
      writer.write("Welcome to the SSH REPL. Type 'exit' to quit.");
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          line = line.trim();
          if (line.isEmpty())
            continue;
          if ("exit".equalsIgnoreCase(line))
            break;

          String[] parts = line.split("\\s+");
          String cmd = parts[0].toLowerCase(Locale.ROOT);
          String[] args = Arrays.copyOfRange(parts, 1, parts.length);

          switch (cmd) {
            case "todo":
              if (args.length < 3) {
                writer.println("Usage: todo \"<title>\" \"<description>\" 1h|2d|60s|20m");
              } else {
                var scheduledFor = LocalDateTime.now().plus(parseDuration(args[2].trim()));
                reminderTaskService.save(ReminderTask.builder()
                    .title(args[0].trim()).description(args[1].trim()).sendMail(true)
                    .specificDate(DateHelper.toDate(scheduledFor))
                    .build(), true);
                writer.println("task created");
              }
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
