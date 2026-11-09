package tech.artcoded.websitev2.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import tech.artcoded.event.v1.timesheet.TimesheetDeleted;

class SerializeEventTest {
    static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void testSerialize() throws Exception {
        var event = TimesheetDeleted.builder().timesheetId("123").clientName("test").period("Q3-2023").build();

        var eventJson = MAPPER.writeValueAsString(event);
        var deserialized = MAPPER.readValue(eventJson, TimesheetDeleted.class);
        assertThat(deserialized).isEqualTo(event);
    }
}
