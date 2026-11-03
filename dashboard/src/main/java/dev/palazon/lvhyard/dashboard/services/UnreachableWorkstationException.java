package dev.palazon.lvhyard.dashboard.services;

/** Its Consul datacenter did not answer, so its Services are not known for now. */
public class UnreachableWorkstationException extends RuntimeException {

  UnreachableWorkstationException(String workstation, Throwable cause) {
    super(
        "Workstation "
            + workstation
            + " is unreachable: its Consul datacenter does not answer. Its Services"
            + " show again by themselves once it does.",
        cause);
  }
}
