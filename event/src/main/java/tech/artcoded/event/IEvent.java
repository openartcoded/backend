package tech.artcoded.event;

public interface IEvent {
  enum Version {V1}

  default long getTimestamp() {
    return System.currentTimeMillis();
  }

  Version getVersion();

  default String getEventName() {
    return this.getClass().getSimpleName();
  }
}
