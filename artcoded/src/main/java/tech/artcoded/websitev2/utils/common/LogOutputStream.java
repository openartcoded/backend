package tech.artcoded.websitev2.utils.common;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;

/**
 * Redirects an {@link OutputStream} (e.g. the output of an external process) to a logger, one log entry per line.
 * Bytes are buffered and decoded as UTF-8 per line, so multi-byte characters are no longer split into garbage as when
 * each byte was cast to a char.
 */
public class LogOutputStream extends OutputStream {
    private final Logger logger;

    /** The internal memory for the written bytes. */
    private final ByteArrayOutputStream mem = new ByteArrayOutputStream();

    public LogOutputStream(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public synchronized void write(final int b) {
        if (b == '\n') {
            flush();
            return;
        }
        mem.write(b);
    }

    @Override
    public synchronized void flush() {
        var line = mem.toString(StandardCharsets.UTF_8);
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        logger.info(line);
        mem.reset();
    }
}
