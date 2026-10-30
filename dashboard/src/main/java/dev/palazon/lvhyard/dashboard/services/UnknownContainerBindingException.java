package dev.palazon.lvhyard.dashboard.services;

/** The Service has no Container Binding with this ID on this Workstation. */
public class UnknownContainerBindingException extends RuntimeException {

    UnknownContainerBindingException(String service, String id) {
        super("The Service " + service + " has no Container Binding " + id + " on this Workstation.");
    }
}
