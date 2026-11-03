package dev.palazon.lvhyard.dashboard.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One running copy of a Service.
 *
 * @param id the Instance's registration ID, which the Service's Instances path names it by
 * @param host the address it listens on
 * @param port the port it listens on
 * @param health the result of its checks
 * @param check how its Health is checked
 * @param tags words that say what it is for
 * @param meta notes about it, by key
 * @param checks the last result of each check its Health comes from: its own and its Workstation's
 * @param outOfRange whether it is an Out-of-range Instance: an Instance of a Remote Service at an
 *     address only its Home Workstation can connect to, such as loopback or a Docker network, so
 *     this Workstation never routes to it
 * @param containerBinding the ID of the Container Binding that made it a Bound Instance; {@code
 *     null}, and left out of the JSON, for a Static Instance
 * @param unpublishedContainerPort whether it is a Bound Instance at its container's own address, on
 *     a container port that is not published on the host, so no other Workstation can reach it;
 *     left out of the JSON when false
 * @param core whether it is the Instance of a Core Service, which LVH Yard registers and keeps
 *     itself; left out of the JSON when false
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
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean unpublishedContainerPort,
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean core) {

  /**
   * Whether a Container Binding made it, so it comes and goes with its container and cannot be
   * edited.
   */
  public boolean bound() {
    return containerBinding != null;
  }

  /** {@code host:port}, where the Instance is reached. */
  public String address() {
    return address(host, port);
  }

  /** Whether it is on a loopback address, which only its own Workstation can connect to. */
  public boolean onLoopback() {
    return loopback(host);
  }

  /**
   * Whether {@code host} is a loopback address, such as {@code 127.0.0.1}, {@code ::1} or {@code
   * localhost}. A hostname is not looked up, so one that only resolves to loopback is not seen.
   */
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
  static String address(String host, int port) {
    return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
  }
}
