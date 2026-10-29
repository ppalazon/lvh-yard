package dev.palazon.lvhyard.dashboard.services;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.consul.Consul;
import dev.palazon.lvhyard.dashboard.consul.Consul.AgentService;
import dev.palazon.lvhyard.dashboard.consul.Consul.Check;
import dev.palazon.lvhyard.dashboard.consul.Consul.Registration;

/**
 * Adds and removes this Workstation's Static Instances, after checking the Yard's rules. The only writer of
 * registrations on the local agent.
 */
@Component
public class Registrations {

    /** A single DNS label: what a hostname's first part can be. */
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    /** An IP address or a hostname. Nothing that would break a URL, such as /, ?, % or a space. */
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9.:-]+");

    /** Marks the registrations that are Static Instances, so nothing else is removed as one. */
    private static final Map<String, String> STATIC_INSTANCE = Map.of("lvh-yard-instance", "static");

    /** How often a check runs, unless the Instance says otherwise. */
    private static final String CHECK_INTERVAL = "10s";

    /** How long a check waits for an answer, unless the Instance says otherwise. */
    private static final String CHECK_TIMEOUT = "2s";

    private final Workstation workstation;

    private final Consul consul;

    Registrations(Workstation workstation, Consul consul) {
        this.workstation = workstation;
        this.consul = consul;
    }

    /**
     * Registers a Static Instance of {@code service} on {@code workstation}, which must be this one. With no host,
     * it is at this Workstation's LAN address, so other Workstations can reach it. Registering the same host and
     * port again replaces that Instance, which keeps its ID.
     *
     * @return the new Instance, critical until its check first passes
     */
    public Instance register(String workstation, String service, String host, Integer port) {
        checkHome(workstation);
        checkName(service);
        checkPort(port);
        if (host == null || host.isBlank()) {
            host = defaultHost();
        }
        checkHost(host);
        String id = staticInstanceAt(service, host, port).orElseGet(() -> UUID.randomUUID().toString());
        consul.register(new Registration(id, service, host, port, STATIC_INSTANCE, tcpCheck(host, port)));
        return new Instance(id, host, port, Health.CRITICAL);
    }

    /** Where a Static Instance registered without a host is: this Workstation's LAN address. */
    public String defaultHost() {
        return consul.agentAddress();
    }

    /** A port as typed into a form; {@code null} when nothing was typed. */
    public static Integer parsePort(String port) {
        if (port == null || port.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(port.strip());
        } catch (NumberFormatException e) {
            throw notATcpPort(port);
        }
    }

    /** Removes the Static Instance {@code id} of {@code service} on {@code workstation}, which must be this one. */
    public void remove(String workstation, String service, String id) {
        checkHome(workstation);
        consul.agentService(id)
                .filter(registration -> registration.name().equals(service) && isStaticInstance(registration))
                .orElseThrow(() -> new UnknownInstanceException(service, id));
        consul.deregister(id);
    }

    /** The ID of the Static Instance of {@code service} at {@code host} and {@code port}, if there is one. */
    private Optional<String> staticInstanceAt(String service, String host, int port) {
        return consul.agentServices().values().stream()
                .filter(registration -> registration.name().equals(service) && isStaticInstance(registration)
                        && registration.address().equals(host) && registration.port() == port)
                .map(AgentService::id)
                .findFirst();
    }

    private static boolean isStaticInstance(AgentService registration) {
        return registration.meta().entrySet().containsAll(STATIC_INSTANCE.entrySet());
    }

    private void checkHome(String workstation) {
        if (!workstation.equals(this.workstation.name())) {
            throw new RemoteServiceException(workstation);
        }
    }

    private static void checkName(String service) {
        if (!DNS_LABEL.matcher(service).matches()) {
            throw new InvalidRegistrationException("name", "The Service name " + service
                    + " is not a single DNS label: use 1 to 63 lowercase letters, digits and -, not starting or"
                    + " ending with -.");
        }
        if (service.equals(Workstation.RESERVED_NAME)) {
            throw new InvalidRegistrationException("name", "The Service name " + Workstation.RESERVED_NAME
                    + " is taken by the dashboard, at " + Workstation.RESERVED_NAME + ".<base-domain>.");
        }
    }

    private static void checkHost(String host) {
        if (!HOST.matcher(host).matches()) {
            throw new InvalidRegistrationException("host", "The host " + host
                    + " is not an IP address or a hostname: use letters, digits, ., - and :.");
        }
    }

    private static void checkPort(Integer port) {
        if (port == null) {
            throw new InvalidRegistrationException("port", "A Static Instance needs a port.");
        }
        if (port < 1 || port > 65535) {
            throw notATcpPort(String.valueOf(port));
        }
    }

    private static InvalidRegistrationException notATcpPort(String port) {
        return new InvalidRegistrationException("port", "The port " + port + " is not a TCP port: use 1 to 65535.");
    }

    /** The check every Instance without one of its own gets: a TCP connection to its port. */
    private static Check tcpCheck(String host, int port) {
        return new Check(Instance.address(host, port), CHECK_INTERVAL, CHECK_TIMEOUT);
    }
}
