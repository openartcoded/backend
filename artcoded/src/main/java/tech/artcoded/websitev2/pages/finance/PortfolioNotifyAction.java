package tech.artcoded.websitev2.pages.finance;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class PortfolioNotifyAction implements Action {
  public static final String ACTION_KEY = "PORTFOLIO_NOTIFY_ACTION";
  public static final String PARAMETER_PERCENTAGE_DELTA = "PARAMETER_PERCENTAGE_DELTA";

  private static final String NOTIFICATION_TYPE = "TICK_THRESHOLD";
  private final PortfolioRepository portfolioRepository;
  private final NotificationService notificationService;
  private final HttpClient httpclient = HttpClient.newHttpClient();
  @Value("${yahoo.finance.chart.url}")
  private String yahooFinanceChartUri;

  public PortfolioNotifyAction(PortfolioRepository portfolioRepository, NotificationService notificationService) {
    this.portfolioRepository = portfolioRepository;
    this.notificationService = notificationService;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);

    List<String> messages = new ArrayList<>();
    try {
      messages.add("starting action");
      Long deltaValue = parameters.stream()
        .filter(p -> PARAMETER_PERCENTAGE_DELTA.equals(p.getKey()))
        .filter(p -> StringUtils.isNotEmpty(p.getValue())).findFirst()
        .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(25L);
      messages.add("delta value: " + deltaValue);
      portfolioRepository.findAll()
        .stream().flatMap(p -> p.getTicks().stream())
        .distinct()
        .peek(tick -> messages.add("search for tick " + tick.getSymbol()))
        .forEach(tick -> {
          try {
            var response = httpclient.send(
              HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("%s/%s?interval=1d&range=1d&region=EU".formatted(yahooFinanceChartUri, tick.getSymbol())))
                .build(), HttpResponse.BodyHandlers.ofString());
            Double currentPrice = JsonPath.read(response.body(), "$.chart.result[0].meta.regularMarketPrice");
            BigDecimal delta = (new BigDecimal(currentPrice).subtract(tick.getPriceWhenAdded())
              .divide(tick.getPriceWhenAdded(), RoundingMode.HALF_UP)
              .multiply(new BigDecimal(100)));

            String deltaMessage = "%s: %s%s".formatted(tick.getSymbol(), delta.setScale(2, RoundingMode.HALF_DOWN)
              .toString(), "%");
            messages.add(deltaMessage);

            if (delta.doubleValue() <= -deltaValue) {
              log.debug(deltaMessage);
              this.notificationService.sendEvent(deltaMessage, NOTIFICATION_TYPE, IdGenerators.get());
            }

          } catch (Exception e) {
            log.error("error during delta calculation for tick {}, error {}", tick, e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            resultBuilder.status(StatusType.FAILURE).messages(messages).finishedDate(new Date());
          }

        });
      return resultBuilder.messages(messages).finishedDate(new Date()).build();
    } catch (Exception e) {
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
    }

  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
      .key(ACTION_KEY)
      .title("Portfolio Notify Action")
      .description("An action to notify when a tick (e.g TSLA) under perform to a defined percentage (default: 25%)")
      .allowedParameters(List.of(ActionParameter.builder()
        .key(PARAMETER_PERCENTAGE_DELTA)
        .parameterType(ActionParameterType.LONG)
        .required(false)
        .description("Represent a percentage delta that triggers the notification. Between 1-100. Default is 25")
        .build()))
      .defaultCronValue("0 30 0 * * *")
      .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }

}
