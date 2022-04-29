package tech.artcoded.websitev2.event;

public interface IEvent {
  default long getTimestamp() {
    return System.currentTimeMillis();
  }

  String getEventName();
}
