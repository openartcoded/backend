package tech.artcoded.websitev2.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import tech.artcoded.event.v1.timesheet.TimesheetDeleted;

public class SerializeEventTest {
    static ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testSerialize() throws Exception {
        var event = TimesheetDeleted.builder().timesheetId("123").clientName("test").period("Q3-2023").build();

        var eventJson = MAPPER.writeValueAsString(event);
        assertNotNull(event);
        var deserialized = MAPPER.readValue(eventJson, TimesheetDeleted.class);
        assertEquals(event, deserialized);

    }
}
