package tech.artcoded.websitev2.actuator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.system.ApplicationPid;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@EndpointWebExtension(endpoint = InfoEndpoint.class)
@Slf4j
public class InfoWebEndpointExtension {

  private final InfoEndpoint delegate;

  private final String pid;

  public InfoWebEndpointExtension(InfoEndpoint delegate) {
    this.delegate = delegate;
    this.pid = new ApplicationPid().toString();
    log.info("app pid {}", pid);
  }

  @ReadOperation
  public WebEndpointResponse<Map<String, Object>> info() {
    Map<String, Object> info = new HashMap<>(this.delegate.info());
    info.put("pid", pid);
    return new WebEndpointResponse<>(info, 200);
  }
}
