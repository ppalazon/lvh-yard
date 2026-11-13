package cc.lvhy.yard.dashboard.consul;

import java.util.List;
import java.util.Map;

/**
 * Only the calls LVH Yard makes, with Consul's own names, in five roles. A class asks for the role
 * that it uses, and this is all of them. A read can name another datacenter, which the agent
 * forwards over WAN federation. {@code null} names the agent's own. {@link ConsulAgent} is the real
 * one.
 */
public interface Consul
    extends ConsulFederation, ConsulRegistrations, ConsulCatalog, ConsulKv, ConsulWatching {

  /**
   * The name and ID of the Consul server's own entry in its catalog, which is no Instance of the
   * Yard.
   */
  String CONSUL_SERVICE = "consul";

  static boolean isServerItself(AgentService registration) {
    return registration.name().equals(CONSUL_SERVICE) && registration.id().equals(CONSUL_SERVICE);
  }

  /** {@code status} is alive, leaving, left or failed. The {@code dc} tag is its datacenter. */
  record WanMember(String address, int port, SerfStatus status, Map<String, String> tags) {

    public WanMember {
      status = status == null ? SerfStatus.UNKNOWN : status;
      tags = tags == null ? Map.of() : tags;
    }

    /** The datacenter, which is its Workstation's name. */
    public String datacenter() {
      return tags.getOrDefault("dc", "");
    }

    public boolean alive() {
      return status == SerfStatus.ALIVE;
    }
  }

  /** Consul sends it as Serf's code. */
  enum SerfStatus {
    UNKNOWN,
    ALIVE,
    LEAVING,
    LEFT,
    FAILED;

    /** 1 alive, 2 leaving, 3 left, 4 failed. */
    public static SerfStatus of(int code) {
      SerfStatus[] all = values();
      return code > 0 && code < all.length ? all[code] : UNKNOWN;
    }

    @Override
    public String toString() {
      return name().toLowerCase();
    }
  }

  record Registration(
      String id,
      String name,
      String address,
      int port,
      List<String> tags,
      Map<String, String> meta,
      Check check) {}

  /** Consul starts every check as critical until it first passes. */
  record Check(String name, String http, String tcp, String interval, String timeout) {}

  /** Consul leaves out a port of 0 and empty meta. */
  record AgentService(
      String id,
      String name,
      String address,
      Integer port,
      List<String> tags,
      Map<String, String> meta) {

    public AgentService {
      if (port == null) {
        port = 0;
      }
      tags = tags == null ? List.of() : tags;
      meta = meta == null ? Map.of() : meta;
    }
  }

  record HealthEntry(AgentService service, List<CheckStatus> checks) {}

  /**
   * Maintenance counts as critical. A node's check has no service ID, type, interval or timeout.
   * Consul gives durations in Go format, such as {@code 1m30s}.
   */
  record CheckStatus(
      String name,
      String status,
      String output,
      String serviceId,
      String type,
      String interval,
      String timeout) {}
}
