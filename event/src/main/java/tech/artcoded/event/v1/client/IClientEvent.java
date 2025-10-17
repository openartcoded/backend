package tech.artcoded.event.v1.client;

import tech.artcoded.event.IEvent;

public non-sealed interface IClientEvent extends IEvent {
    @Override
    default Version getVersion() {
        return Version.V1;
    }
}
