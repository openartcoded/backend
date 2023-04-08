package tech.artcoded.websitev2.script;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScriptLoggingOutputStream extends OutputStream {

    private final ByteArrayOutputStream baos = new ByteArrayOutputStream(1000);
    private final LogLevel level;

    public enum LogLevel {
      TRACE, DEBUG, INFO, WARN, ERROR,
    }

    public ScriptLoggingOutputStream(LogLevel level) {
      this.level = level;
    }

    @Override
    public void write(int b) throws IOException {
      if (b == '\n') {
        String line = baos.toString();
        baos.reset();

        switch (level) {
          case TRACE:
            log.trace(line);
            break;
          case DEBUG:
            log.debug(line);
            break;
          case ERROR:
            log.error(line);
            break;
          case INFO:
            log.info(line);
            break;
          case WARN:
            log.warn(line);
            break;
        }
      } else {
        baos.write(b);
      }
    }

  }

}
