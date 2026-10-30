package dev.palazon.lvhyard.dashboard.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.AgentService;
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

    /** One number and unit of a Go duration, such as the {@code 1m} and the {@code 30s} of {@code 1m30s}. */
    private static final Pattern GO_DURATION_PART = Pattern.compile("(\\d+(?:\\.\\d+)?)(h|ms|m|s|us|µs|ns)");

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
        AgentService registration = entry.service();
        List<CheckResult> checks = entry.checks().stream()
                .map(check -> new CheckResult(check.name(), Health.ofConsulStatus(check.status()),
                        check.output() == null ? "" : check.output()))
                .toList();
        HealthCheck check = entry.checks().stream()
                .filter(consulCheck -> registration.id().equals(consulCheck.serviceId()))
                .findFirst()
                .map(consulCheck -> new HealthCheck(consulCheck.type(),
                        registration.meta().get(Registrations.HTTP_PATH),
                        HealthCheck.format(goDuration(consulCheck.interval())),
                        HealthCheck.format(goDuration(consulCheck.timeout()))))
                .orElse(null);
        Map<String, String> meta = new TreeMap<>(registration.meta());
        meta.keySet().removeIf(key -> key.startsWith(Registrations.RESERVED_META));
        return new Instance(registration.id(), registration.address(), registration.port(),
                Health.worst(checks.stream().map(CheckResult::health).toList()), check, registration.tags(), meta,
                checks);
    }

    /** A duration as Consul writes it, in Go's way, such as {@code 1m30s} or {@code 1.5s}. */
    private static Duration goDuration(String text) {
        BigDecimal nanos = BigDecimal.ZERO;
        Matcher part = GO_DURATION_PART.matcher(text == null ? "" : text);
        while (part.find()) {
            BigDecimal unit = switch (part.group(2)) {
                case "h" -> BigDecimal.valueOf(3_600_000_000_000L);
                case "m" -> BigDecimal.valueOf(60_000_000_000L);
                case "s" -> BigDecimal.valueOf(1_000_000_000L);
                case "ms" -> BigDecimal.valueOf(1_000_000L);
                case "us", "µs" -> BigDecimal.valueOf(1_000L);
                default -> BigDecimal.ONE;
            };
            nanos = nanos.add(new BigDecimal(part.group(1)).multiply(unit));
        }
        return Duration.ofNanos(nanos.longValue());
    }
}
