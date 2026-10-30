package dev.palazon.lvhyard.dashboard.services;

/** The Yard has no Workstation of this name: neither this one nor any datacenter federated with it. */
public class UnknownWorkstationException extends RuntimeException {

    UnknownWorkstationException(String workstation) {
        super("The Yard has no Workstation " + workstation + ".");
    }
}
