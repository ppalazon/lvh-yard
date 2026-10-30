package dev.palazon.lvhyard.dashboard.services;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.AgentService;
import dev.palazon.lvhyard.dashboard.consul.Consul.HealthEntry;

/**
 * Reads the Services of the Yard, with their Instances, Health and Publications, from every Workstation's Consul
 * datacenter, and gives each published one its hostname: the one rule for hostnames, which the Routes and the pages
 * both follow. A datacenter that does not answer makes its Workstation unreachable; the others still load. This
 * Workstation's own Services are at {@code <service>.<base-domain>} (ADR-0003); Remote Services get no hostname
 * here until a Publication can have Yard Scope.
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
     * Every Workstation of the Yard with its Services: this one first, then the others by name. The others are read
     * side by side, so a Workstation that does not answer delays the rest only once.
     */
    public List<HomeWorkstation> workstations() {
        try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<HomeWorkstation>> others = consul.datacenters().stream()
                    .filter(datacenter -> !datacenter.equals(workstation.name()))
                    .sorted()
                    .map(other -> CompletableFuture.supplyAsync(() -> remote(other), readers))
                    .toList();
            List<HomeWorkstation> workstations = new ArrayList<>();
            workstations.add(new HomeWorkstation(workstation.name(), false, read(workstation.name())));
            others.forEach(other -> workstations.add(other.join()));
            return workstations;
        }
    }

    /** Every Service of the Workstations that answer, this Workstation's first, then by Workstation and name. */
    public List<Service> services() {
        return workstations().stream().flatMap(home -> home.services().stream()).toList();
    }

    /**
     * The Services of {@code workstation}, ordered by name; none when the Yard has no such Workstation.
     *
     * @throws UnreachableWorkstationException when its datacenter does not answer
     */
    public List<Service> services(String workstation) {
        return inYard(workstation) ? read(workstation) : List.of();
    }

    /**
     * The Service called {@code name} on {@code workstation}, while it has an Instance or a Publication.
     *
     * @throws UnreachableWorkstationException when the Workstation's datacenter does not answer
     */
    public Optional<Service> service(String workstation, String name) {
        if (!inYard(workstation)) {
            return Optional.empty();
        }
        return reading(workstation, datacenter -> service(workstation, datacenter, name,
                publications.all(datacenter).get(name)));
    }

    private HomeWorkstation remote(String workstation) {
        try {
            return new HomeWorkstation(workstation, false, read(workstation));
        } catch (UnreachableWorkstationException e) {
            return new HomeWorkstation(workstation, true, List.of());
        }
    }

    private boolean inYard(String workstation) {
        return workstation.equals(this.workstation.name()) || consul.datacenters().contains(workstation);
    }

    /**
     * Every Service of {@code workstation}, ordered by name. A Service is there while it has an Instance or a
     * Publication.
     */
    private List<Service> read(String workstation) {
        return reading(workstation, datacenter -> {
            Map<String, Publication> published = publications.all(datacenter);
            TreeSet<String> names = new TreeSet<>(consul.serviceNames(datacenter));
            names.remove(Consul.CONSUL_SERVICE);
            names.addAll(published.keySet());
            return names.stream()
                    .flatMap(name -> service(workstation, datacenter, name, published.get(name)).stream())
                    .toList();
        });
    }

    /**
     * What {@code read} gives for {@code workstation}'s datacenter: {@code null}, the agent's own, for this
     * Workstation. Another Workstation's datacenter that does not answer in time, or that Consul has no path to (a
     * 5xx), makes it unreachable; any other error is a fault, not an unreachable Workstation.
     */
    private <T> T reading(String workstation, Function<String, T> read) {
        if (workstation.equals(this.workstation.name())) {
            return read.apply(null);
        }
        try {
            return read.apply(workstation);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new UnreachableWorkstationException(workstation, e);
        }
    }

    private Optional<Service> service(String home, String datacenter, String name, Publication publication) {
        List<Instance> instances = consul.health(datacenter, name).stream().map(YardReader::instance)
                .sorted(BY_ADDRESS).toList();
        if (instances.isEmpty() && publication == null) {
            return Optional.empty();
        }
        String hostname = publication != null && datacenter == null ? workstation.hostname(name) : null;
        return Optional.of(new Service(home, name, publication, hostname, instances));
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
