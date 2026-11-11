package cc.lvhy.yard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code checks} include its Workstation's own. Container fields are left out of the JSON for a
 * Static Instance. {@code hostPort} is left out when the Container Port has no Host Port.
 *
 * @param foreign whether another tool registered it in Consul, so LVH Yard cannot change it
 */
public record Instance(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String host,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int port,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Health health,
    @JsonInclude(JsonInclude.Include.NON_NULL) HealthCheck check,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> tags,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, String> meta,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CheckResult> checks,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean outOfRange,
    @JsonInclude(JsonInclude.Include.NON_NULL) String containerBinding,
    @JsonInclude(JsonInclude.Include.NON_NULL) String container,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer containerPort,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer hostPort,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean unpublishedContainerPort,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean foreign) {

  /** So it comes and goes with its container, and cannot be edited. */
  public boolean bound() {
    return containerBinding != null;
  }

  /** Whether LVH Yard registered it at a fixed host and port, so it can edit and remove it. */
  public boolean staticInstance() {
    return !bound() && !foreign;
  }

  public String address() {
    return address(host, port);
  }

  public boolean onLoopback() {
    return loopback(host);
  }

  /** A hostname is not looked up, so one that only resolves to loopback is not seen. */
  private static boolean loopback(String host) {
    try {
      return InetAddress.ofLiteral(host).isLoopbackAddress();
    } catch (IllegalArgumentException notAnAddress) {
      String name = host.toLowerCase(Locale.ROOT);
      name = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
      return name.equals("localhost") || name.endsWith(".localhost");
    }
  }

  /** {@code host:port}, with an IPv6 host in brackets so its colons are not read as the port's. */
  public static String address(String host, int port) {
    return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
  }
}
