package dev.palazon.lvhyard.dashboard.services;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.HealthEntry;

/** Reads the Services of the Yard, with their Instances and Health. For now, only this Workstation's datacenter. */
@Component
public class YardReader {

    private static final Comparator<Instance> BY_ADDRESS =
            Comparator.comparing(Instance::host).thenComparingInt(Instance::port);

    private final Workstation workstation;

    private final Consul consul;

    YardReader(Workstation workstation, Consul consul) {
        this.workstation = workstation;
        this.consul = consul;
    }

    /** Every Service, ordered by Workstation and name. A Service with no Instances left is gone. */
    public List<Service> services() {
        return consul.serviceNames().stream()
                .filter(name -> !name.equals(Consul.CONSUL_SERVICE))
                .sorted()
                .flatMap(name -> service(workstation.name(), name).stream())
                .toList();
    }

    /** The Services of {@code workstation}, ordered by name. */
    public List<Service> services(String workstation) {
        return services().stream().filter(service -> service.workstation().equals(workstation)).toList();
    }

    /** The Service called {@code name} on {@code workstation}, while it has any Instances. */
    public Optional<Service> service(String workstation, String name) {
        if (!workstation.equals(this.workstation.name())) {
            return Optional.empty();
        }
        List<Instance> instances = consul.health(name).stream().map(YardReader::instance).sorted(BY_ADDRESS)
                .toList();
        return instances.isEmpty() ? Optional.empty() : Optional.of(new Service(workstation, name, instances));
    }

    private static Instance instance(HealthEntry entry) {
        Health health = Health.worst(entry.checks().stream().map(check -> Health.ofConsulStatus(check.status()))
                .toList());
        return new Instance(entry.service().id(), entry.service().address(), entry.service().port(), health);
    }
}
