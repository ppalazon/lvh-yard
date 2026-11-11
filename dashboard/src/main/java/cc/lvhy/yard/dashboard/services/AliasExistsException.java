package cc.lvhy.yard.dashboard.services;

/** A Service has one Alias at most to each Remote Service, so its order has no repeats. */
public class AliasExistsException extends RuntimeException {

  AliasExistsException(String service, String id) {
    super(service + " already has an Alias to " + id + ".");
  }
}
