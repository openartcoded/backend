package tech.artcoded.event.v1.dossier;

import tech.artcoded.event.IEvent;

public non-sealed interface IDossierEvent extends IEvent {
    @Override
    default Version getVersion() {
        return Version.V1;
    }
}
