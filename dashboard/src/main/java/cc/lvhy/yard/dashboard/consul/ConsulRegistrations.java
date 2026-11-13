package cc.lvhy.yard.dashboard.consul;

import cc.lvhy.yard.dashboard.consul.Consul.AgentService;
import cc.lvhy.yard.dashboard.consul.Consul.Registration;
import java.util.Map;
import java.util.Optional;

/** The registrations on the local agent. */
public interface ConsulRegistrations {

  /** compose.yaml sets it to the Workstation's LAN address. */
  String agentAddress();

  /** Replaces the registration with the same ID. */
  void register(Registration registration);

  Map<String, AgentService> agentServices();

  Optional<AgentService> agentService(String id);

  void deregister(String id);
}
