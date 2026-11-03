package dev.palazon.lvhyard.dashboard.services;

import dev.palazon.lvhyard.dashboard.Workstation;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Aliases never change what other Workstations see, as no other Workstation reads them. */
@Component
public class Aliases {

  private final Workstation workstation;

  private final AliasKeys keys;

  private final YardReader yard;

  Aliases(Workstation workstation, AliasKeys keys, YardReader yard) {
    this.workstation = workstation;
    this.keys = keys;
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
          "An Alias is only for a Remote Service. "
              + service
              + " of this Workstation already has the short hostname "
              + workstation.hostname(service)
              + ".");
    }
    if (yard.service(home, service).isEmpty()) {
      throw new UnknownServiceException(home, service);
    }
    keys.put(name, new AliasKeys.Target(home, service));
    return of(yard.routableYard()).stream()
        .filter(alias -> alias.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  public void remove(String name) {
    if (!keys.contains(name)) {
      throw new UnknownAliasException(name);
    }
    keys.delete(name);
  }

  /**
   * An Alias to a Workstation whose Services fail to read is unreachable, because it has no Route.
   */
  public List<Alias> all() {
    return of(yard.routableYard());
  }

  public List<Alias> of(Yard yard) {
    return keys.targets().entrySet().stream()
        .map(entry ->
            alias(entry.getKey(), entry.getValue().workstation(), entry.getValue().service(), yard))
        .toList();
  }

  /**
   * A published Service of this Workstation with the same name takes the hostname. The Alias is
   * then its last fallback, unless its Instance Priority leaves out the {@code alias} kind.
   * Otherwise the Alias is active by the same rules that give the Service its own Route here.
   */
  private Alias alias(String name, String home, String service, Yard yard) {
    Alias.State state;
    String reason;
    Optional<YardWorkstation> remote =
        yard.remotes().stream().filter(candidate -> candidate.name().equals(home)).findFirst();
    Optional<Service> target = yard.remoteService(home, service);
    Optional<Service> own =
        yard.home().services().stream()
            .filter(candidate -> candidate.name().equals(name) && candidate.published())
            .findFirst();
    if (own.isPresent() && !own.get().priority().contains(InstanceKind.ALIAS)) {
      state = Alias.State.HIDDEN;
      reason =
          "Hidden by this Workstation's published Service "
              + name
              + ", whose Instance Priority leaves out the Alias.";
    } else if (remote.isEmpty()) {
      state = Alias.State.MISSING;
      reason = "The Yard has no Workstation " + home + ".";
    } else if (remote.get().unreachable()) {
      state = Alias.State.UNREACHABLE;
      reason = "Workstation " + home + " is unreachable: its Consul does not answer.";
    } else if (target.isEmpty()) {
      state = Alias.State.MISSING;
      reason = "Workstation " + home + " has no Service " + service + ".";
    } else if (!target.get().published()) {
      state = Alias.State.NOT_SHARED;
      reason = home + " has not published " + service + ".";
    } else if (!target.get().sharedWithYard()) {
      state = Alias.State.NOT_SHARED;
      reason = home + " publishes " + service + " with Local Scope only.";
    } else if (target.get().instances().stream().allMatch(Instance::outOfRange)) {
      state = Alias.State.UNAVAILABLE;
      reason = service + " has no Instance this Workstation can reach.";
    } else if (own.isPresent()) {
      state = Alias.State.FALLBACK;
      reason =
          own.get().routing() != null && own.get().routing().kind() == InstanceKind.ALIAS
              ? "In use now, as no Instance of this Workstation's Service " + name + " passes."
              : "The last fallback for this Workstation's Service "
                  + name
                  + ": used when none of its Instances passes.";
    } else {
      state = Alias.State.ACTIVE;
      reason = null;
    }
    return new Alias(name, home, service, workstation.hostname(name), state, reason);
  }
}
