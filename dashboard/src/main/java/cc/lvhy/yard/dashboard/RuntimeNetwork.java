package cc.lvhy.yard.dashboard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Windows uses IPv4 sockets; other runtimes keep their address families. */
@ConfigurationProperties("lvh-yard")
public record RuntimeNetwork(boolean ipv4Only) {

  public static final String IPV4_TARGETS_ONLY =
      "Windows runtime supports IPv4 targets only. Use an IPv4 address or a hostname with an IPv4"
          + " address.";

  /** A colon marks an explicit IPv6 target, including an IPv4-mapped address. No DNS lookup. */
  public boolean supports(String host) {
    return !ipv4Only || host == null || host.indexOf(':') < 0;
  }
}
