package tech.artcoded.event.v1.timesheet;

import tech.artcoded.event.IEvent;

public non-sealed interface ITimesheetEvent extends IEvent {
    @Override
    default Version getVersion() {
        return Version.V1;
    }
}
