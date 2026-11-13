package cc.lvhy.yard.dashboard;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * From {@code YARD_OUT_OF_RANGE}, by default loopback and 172.17.0.0/16 to 172.31.0.0/16. A Route
 * to such an address would reach this Workstation's own loopback or containers.
 */
@ConfigurationProperties("lvh-yard.out-of-range")
public record OutOfRange(List<String> ranges) {

  /**
   * Checks each range now, so a mistyped one stops the dashboard from starting instead of matching
   * nothing.
   */
  public OutOfRange {
    ranges = ranges == null ? List.of() : List.copyOf(ranges);
    ranges.forEach(OutOfRange::parse);
  }

  /**
   * {@code localhost} and names under it count as loopback. Any other hostname is not looked up, so
   * it is never out of range.
   */
  public boolean contains(String host) {
    List<InetAddress> addresses;
    try {
      addresses = List.of(InetAddress.ofLiteral(host));
    } catch (IllegalArgumentException notAnAddress) {
      String name = host.toLowerCase(Locale.ROOT);
      name = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
      if (!name.equals("localhost") && !name.endsWith(".localhost")) {
        return false;
      }
      addresses = List.of(InetAddress.ofLiteral("127.0.0.1"), InetAddress.ofLiteral("::1"));
    }
    return addresses.stream()
        .anyMatch(address -> ranges.stream().anyMatch(range -> parse(range).contains(address)));
  }

  private static Range parse(String range) {
    String[] parts = range.strip().split("/", 2);
    try {
      byte[] network = InetAddress.ofLiteral(parts[0]).getAddress();
      int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
      if (prefix < 0 || prefix > network.length * 8) {
        throw new IllegalArgumentException("prefix out of bounds");
      }
      return new Range(network, prefix);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "YARD_OUT_OF_RANGE has "
              + range
              + ", which is not an address and a"
              + " prefix length such as 172.17.0.0/16.",
          e);
    }
  }

  private record Range(byte[] network, int prefix) {

    boolean contains(InetAddress address) {
      byte[] bytes = address.getAddress();
      if (bytes.length != network.length) {
        return false;
      }
      for (int bit = 0; bit < prefix; bit++) {
        int mask = 0x80 >>> (bit % 8);
        if ((bytes[bit / 8] & mask) != (network[bit / 8] & mask)) {
          return false;
        }
      }
      return true;
    }
  }
}
