package dev.palazon.lvhyard.dashboard.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;

/**
 * This Workstation's Aliases, in its own Consul KV: one key per Alias, holding the Remote Service it names as
 * {@code <workstation>/<service>}. No other Workstation reads them, so an Alias never changes what others see.
 */
@Component
public class Aliases {

    /** Where the Aliases live in the KV store, under LVH Yard's own prefix. */
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
     * Gives {@code service} of {@code home}, another Workstation, the Alias {@code name}, or points the Alias there if
     * it exists. The Remote Service needs no Yard Scope yet: until it has one, the Alias has no Route.
     *
     * @throws UnknownWorkstationException when the Yard has no Workstation {@code home}
     * @throws UnreachableWorkstationException when {@code home} does not answer, so its Services are not known
     * @throws UnknownServiceException when {@code home} has no Service {@code service}
     */
    public Alias add(String name, String home, String service) {
        Registrations.checkName("Alias", name);
        if (workstation.is(home)) {
            throw new InvalidRegistrationException("workstation", "An Alias names a Remote Service: " + service
                    + " of this Workstation already has the short hostname " + workstation.hostname(service) + ".");
        }
        if (yard.service(home, service).isEmpty()) {
            throw new UnknownServiceException(home, service);
        }
        consul.putValue(PREFIX + name, home + "/" + service);
        return of(yard.routableYard()).stream().filter(alias -> alias.name().equals(name)).findFirst()
                .orElseThrow();
    }

    /**
     * Removes the Alias {@code name}, and with it its Route.
     *
     * @throws UnknownAliasException when this Workstation has no such Alias
     */
    public void remove(String name) {
        if (!consul.values(null, PREFIX).containsKey(PREFIX + name)) {
            throw new UnknownAliasException(name);
        }
        consul.deleteValue(PREFIX + name);
    }

    /**
     * Every Alias of this Workstation, by name, each with its state in the Yard as the Routes see it: an Alias to a
     * Workstation whose Services fail to read is unreachable, as it has no Route.
     */
    public List<Alias> all() {
        return of(yard.routableYard());
    }

    /** Every Alias of this Workstation, by name, each with its state in {@code yard}. */
    public List<Alias> of(Yard yard) {
        return consul.values(null, PREFIX).entrySet().stream().map(entry -> {
            String name = entry.getKey().substring(PREFIX.length());
            String[] target = entry.getValue().split("/", 2);
            String home = target[0];
            String service = target.length < 2 ? "" : target[1];
            return alias(name, home, service, yard);
        }).toList();
    }

    /**
     * The Alias {@code name} of {@code service} of {@code home}, with its state in {@code yard}. A published Service
     * of this Workstation with the same name wins over it, as its Route has the same hostname. Otherwise it is active
     * while the Remote Service has Yard Scope and an Instance in range: the rules that give the Service its own Route
     * here.
     */
    private Alias alias(String name, String home, String service, Yard yard) {
        Alias.State state;
        String reason;
        Optional<YardWorkstation> remote = yard.remotes().stream()
                .filter(candidate -> candidate.name().equals(home)).findFirst();
        Optional<Service> target = remote.stream().flatMap(found -> found.services().stream())
                .filter(candidate -> candidate.name().equals(service)).findFirst();
        if (yard.home().services().stream().anyMatch(own -> own.name().equals(name) && own.published())) {
            state = Alias.State.HIDDEN;
            reason = "Hidden: this Workstation's Service " + name + " is published at the same hostname, and wins.";
        } else if (remote.isEmpty()) {
            state = Alias.State.MISSING;
            reason = "The Yard has no Workstation " + home + ".";
        } else if (remote.get().unreachable()) {
            state = Alias.State.UNREACHABLE;
            reason = "Workstation " + home + " is unreachable: its Consul datacenter does not answer. The Alias is"
                    + " routed again by itself once it does.";
        } else if (target.isEmpty()) {
            state = Alias.State.MISSING;
            reason = "Workstation " + home + " has no Service " + service + ".";
        } else if (!target.get().published()) {
            state = Alias.State.NOT_SHARED;
            reason = home + " has not published " + service + ", so this Workstation has no Route for it.";
        } else if (!target.get().sharedWithYard()) {
            state = Alias.State.NOT_SHARED;
            reason = home + " publishes " + service + " with Local Scope, so only " + home + " routes it.";
        } else if (target.get().instances().stream().allMatch(Instance::outOfRange)) {
            state = Alias.State.UNAVAILABLE;
            reason = service + " has no Instance this Workstation can reach: none is registered, or each is on "
                    + home + "'s own loopback or Docker network.";
        } else {
            state = Alias.State.ACTIVE;
            reason = null;
        }
        return new Alias(name, home, service, workstation.hostname(name), state, reason);
    }
}
