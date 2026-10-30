package dev.palazon.lvhyard.dashboard.services;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.HealthEntry;

/**
 * Reads the Services of the Yard, with their Instances, Health and Publications, and gives each published one its
 * hostname: the one rule for hostnames, which the Routes and the pages both follow. For now, only this
 * Workstation's datacenter, whose Services are at {@code <service>.<base-domain>} (ADR-0003).
 */
@Component
public class YardReader {

    private static final Comparator<Instance> BY_ADDRESS =
            Comparator.comparing(Instance::host).thenComparingInt(Instance::port);

    private final Workstation workstation;

    private final Consul consul;

    private final Publications publications;

    YardReader(Workstation workstation, Consul consul, Publications publications) {
        this.workstation = workstation;
        this.consul = consul;
        this.publications = publications;
    }

    /**
     * Every Service, ordered by Workstation and name. A Service is there while it has an Instance or a Publication.
     */
    public List<Service> services() {
        Map<String, Publication> published = publications.all();
        TreeSet<String> names = new TreeSet<>(consul.serviceNames());
        names.remove(Consul.CONSUL_SERVICE);
        names.addAll(published.keySet());
        return names.stream()
                .flatMap(name -> service(name, published.get(name)).stream())
                .toList();
    }

    /** The Services of {@code workstation}, ordered by name. */
    public List<Service> services(String workstation) {
        return services().stream().filter(service -> service.workstation().equals(workstation)).toList();
    }

    /** The Service called {@code name} on {@code workstation}, while it has an Instance or a Publication. */
    public Optional<Service> service(String workstation, String name) {
        if (!workstation.equals(this.workstation.name())) {
            return Optional.empty();
        }
        return service(name, publications.all().get(name));
    }

    private Optional<Service> service(String name, Publication publication) {
        List<Instance> instances = consul.health(name).stream().map(YardReader::instance).sorted(BY_ADDRESS)
                .toList();
        return instances.isEmpty() && publication == null
                ? Optional.empty()
                : Optional.of(new Service(workstation.name(), name, publication,
                        publication == null ? null : workstation.hostname(name), instances));
    }

    private static Instance instance(HealthEntry entry) {
        Health health = Health.worst(entry.checks().stream().map(check -> Health.ofConsulStatus(check.status()))
                .toList());
        return new Instance(entry.service().id(), entry.service().address(), entry.service().port(), health);
    }
}
