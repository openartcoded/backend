package tech.artcoded.websitev2.utils.common;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class LogOutputStreamTest {
    @Test
    void writesCompleteLinesAndFlushesRemainingText() throws Exception {
        Logger logger = mock(Logger.class);
        var stream = new LogOutputStream(logger);
        for (byte value : "first\nsecond".getBytes(StandardCharsets.US_ASCII))
            stream.write(value);
        verify(logger).info("first");
        verify(logger, never()).info("second");
        stream.flush();
        verify(logger).info("second");
    }

    @Test
    void multiByteCharactersAreDecodedAsUtf8() throws Exception {
        Logger logger = mock(Logger.class);
        try (var stream = new LogOutputStream(logger)) {
            // written byte per byte like a process output would be
            for (byte value : "Facture n° 12 – réglée ✓\n".getBytes(StandardCharsets.UTF_8))
                stream.write(value);
        }
        verify(logger).info("Facture n° 12 – réglée ✓");
    }

    @Test
    void windowsLineEndingsAreStripped() throws Exception {
        Logger logger = mock(Logger.class);
        var stream = new LogOutputStream(logger);
        stream.write("line\r\n".getBytes(StandardCharsets.US_ASCII));
        verify(logger).info("line");
    }
}
