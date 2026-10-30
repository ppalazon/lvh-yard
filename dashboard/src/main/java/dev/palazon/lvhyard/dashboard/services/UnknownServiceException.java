package dev.palazon.lvhyard.dashboard.services;

/** This Workstation has no Service of this name: no Instance and no Publication. */
public class UnknownServiceException extends RuntimeException {

    UnknownServiceException(String service) {
        super("This Workstation has no Service " + service + ".");
    }
}
