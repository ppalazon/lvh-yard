package dev.palazon.lvhyard.dashboard.services;

/** A change to a Service of another Workstation: only its Home Workstation can change it. */
public class RemoteServiceException extends RuntimeException {

    RemoteServiceException(String workstation) {
        super("The Services of " + workstation + " can be changed only on " + workstation + ".");
    }
}
