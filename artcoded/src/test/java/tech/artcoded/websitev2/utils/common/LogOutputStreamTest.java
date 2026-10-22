package tech.artcoded.websitev2.utils.common;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import static org.mockito.Mockito.*;

class LogOutputStreamTest {
    @Test void writesCompleteLinesAndFlushesRemainingText() throws Exception {
        Logger logger = mock(Logger.class);
        var stream = new LogOutputStream(logger);
        for (byte value : "first\nsecond".getBytes(java.nio.charset.StandardCharsets.US_ASCII)) stream.write(value);
        verify(logger).info("first");
        verify(logger, never()).info("second");
        stream.flush();
        verify(logger).info("second");
    }
}
