package cc.lvhy.yard.dashboard.consul;

import cc.lvhy.yard.dashboard.consul.Consul.WanMember;
import java.util.List;

/** The datacenters of the Yard, and their servers in the WAN pool. */
public interface ConsulFederation {

  /** Every federated datacenter, answering or not. */
  List<String> datacenters();

  /** Empty while this Workstation's datacenter has no leader. */
  default String leader() {
    return leader(null);
  }

  /**
   * Empty while the datacenter has no leader. A datacenter that the agent has no path to fails, so
   * this tells whether the agent can read from it, which its WAN member being alive does not.
   */
  String leader(String datacenter);

  /** The members of the agent's WAN pool: one server for each federated datacenter. */
  List<WanMember> wanMembers();
}
