package tech.artcoded.websitev2.api.common;

public class Constants {
  public static final String NOTIFICATION_HEADER_TITLE = "NotificationTitle";
  public static final String NOTIFICATION_HEADER_TYPE = "NotificationType";
  public static final String CORRELATION_ID = "CorrelationId";
  public static final String NOTIFICATION_ENDPOINT = "jms:topic:notification";
  public static final String EVENT_PUBLISHER_SEDA_ROUTE = "seda:publish-new-event";
}
