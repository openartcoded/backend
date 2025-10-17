package tech.artcoded.websitev2.utils.common;

import java.io.OutputStream;

import org.slf4j.Logger;

public class LogOutputStream extends OutputStream {
    private final Logger logger;

    /** The internal memory for the written bytes. */
    private StringBuffer mem;

    public LogOutputStream(final Logger logger) {
        this.logger = logger;
        mem = new StringBuffer();
    }

    @Override
    public void write(final int b) {
        if ((char) b == '\n') {
            flush();
            return;
        }
        mem = mem.append((char) b);
    }

    @Override
    public void flush() {
        logger.info(mem.toString());
        mem = new StringBuffer();
    }
}
