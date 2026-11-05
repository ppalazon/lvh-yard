package dev.palazon.lvhyard.dashboard.profiles;

/**
 * A save writes more Route Profiles than one Consul transaction takes, so it cannot be all or
 * nothing. Nothing is written.
 */
public class TooManyRouteProfileWritesException extends RuntimeException {

  TooManyRouteProfileWritesException(int writes, int limit) {
    super(
        "This save writes "
            + writes
            + " Route Profiles, with the chains that name them. Consul takes at most "
            + limit
            + " in one save. Save fewer Route Profiles at a time.");
  }
}
