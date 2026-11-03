package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * One Consul KV key per Alias, holding {@code <workstation>/<service>}. No other Workstation reads
 * them, so an Alias never changes what others see.
 */
@Component
public class Aliases {

  private static final String PREFIX = "lvh-yard/aliases/";

  private final Workstation workstation;

  private final Consul consul;

  private final YardReader yard;

  Aliases(Workstation workstation, Consul consul, YardReader yard) {
    this.workstation = workstation;
    this.consul = consul;
    this.yard = yard;
  }

  /**
   * Also points an existing Alias there. The Remote Service needs no Yard Scope yet: until it has
   * one, the Alias has no Route.
   */
  public Alias add(String name, String home, String service) {
    Registrations.checkName("Alias", name);
    if (workstation.is(home)) {
      throw new InvalidRegistrationException(
          "workstation",
          "An Alias names a Remote Service: "
              + service
              + " of this Workstation already has the short hostname "
              + workstation.hostname(service)
              + ".");
    }
    if (yard.service(home, service).isEmpty()) {
      throw new UnknownServiceException(home, service);
    }
    consul.putValue(PREFIX + name, home + "/" + service);
    return of(yard.routableYard()).stream()
        .filter(alias -> alias.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  public void remove(String name) {
    if (!consul.values(null, PREFIX).containsKey(PREFIX + name)) {
      throw new UnknownAliasException(name);
    }
    consul.deleteValue(PREFIX + name);
  }

  /**
   * An Alias to a Workstation whose Services fail to read is unreachable, because it has no Route.
   */
  public List<Alias> all() {
    return of(yard.routableYard());
  }

  public List<Alias> of(Yard yard) {
    return consul.values(null, PREFIX).entrySet().stream()
        .map(entry -> {
          String name = entry.getKey().substring(PREFIX.length());
          String[] target = entry.getValue().split("/", 2);
          String home = target[0];
          String service = target.length < 2 ? "" : target[1];
          return alias(name, home, service, yard);
        })
        .toList();
  }

  /**
   * A published Service of this Workstation with the same name wins, because its Route has the same
   * hostname. Otherwise the Alias is active by the same rules that give the Service its own Route
   * here.
   */
  private Alias alias(String name, String home, String service, Yard yard) {
    Alias.State state;
    String reason;
    Optional<YardWorkstation> remote =
        yard.remotes().stream().filter(candidate -> candidate.name().equals(home)).findFirst();
    Optional<Service> target =
        remote.stream()
            .flatMap(found -> found.services().stream())
            .filter(candidate -> candidate.name().equals(service))
            .findFirst();
    if (yard.home().services().stream()
        .anyMatch(own -> own.name().equals(name) && own.published())) {
      state = Alias.State.HIDDEN;
      reason =
          "Hidden: this Workstation's Service "
              + name
              + " is published at the same hostname, and wins.";
    } else if (remote.isEmpty()) {
      state = Alias.State.MISSING;
      reason = "The Yard has no Workstation " + home + ".";
    } else if (remote.get().unreachable()) {
      state = Alias.State.UNREACHABLE;
      reason =
          "Workstation "
              + home
              + " is unreachable: its Consul datacenter does not answer. The Alias is"
              + " routed again by itself once it does.";
    } else if (target.isEmpty()) {
      state = Alias.State.MISSING;
      reason = "Workstation " + home + " has no Service " + service + ".";
    } else if (!target.get().published()) {
      state = Alias.State.NOT_SHARED;
      reason =
          home + " has not published " + service + ", so this Workstation has no Route for it.";
    } else if (!target.get().sharedWithYard()) {
      state = Alias.State.NOT_SHARED;
      reason =
          home + " publishes " + service + " with Local Scope, so only " + home + " routes it.";
    } else if (target.get().instances().stream().allMatch(Instance::outOfRange)) {
      state = Alias.State.UNAVAILABLE;
      reason =
          service
              + " has no Instance this Workstation can reach: none is registered, or each is on "
              + home
              + "'s own loopback or Docker network.";
    } else {
      state = Alias.State.ACTIVE;
      reason = null;
    }
    return new Alias(name, home, service, workstation.hostname(name), state, reason);
  }
}
