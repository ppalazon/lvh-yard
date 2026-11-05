package dev.palazon.lvhyard.dashboard.web;

import java.util.Arrays;
import java.util.Optional;

/** A Route Profile that the "New profile" button puts in the form, to start from. */
enum RouteProfileExample {
  BASIC_AUTH(
      "basic-auth",
      "Basic auth",
      "team-login",
      """
      # To make the line of a user, run: htpasswd -nbB <user> <password>
      basicAuth:
        users:
          - "alice:$2y$05$replace.this.with.a.real.bcrypt.hash"
      """),
  IP_ALLOW_LIST(
      "ip-allow-list",
      "IP allow list",
      "office-only",
      """
      # Only these addresses reach the Service.
      ipAllowList:
        sourceRange:
          - 192.168.1.0/24
      """),
  HEADERS(
      "headers",
      "Headers",
      "behind-https",
      """
      # Tells the Service that it is behind HTTPS.
      headers:
        customRequestHeaders:
          X-Forwarded-Proto: https
      """),
  CORS(
      "cors",
      "CORS",
      "cors",
      """
      # Lets a web app on another hostname call the Service.
      headers:
        accessControlAllowOriginList:
          - "http://app.lvh.me"
        accessControlAllowMethods: [GET, POST, OPTIONS]
        accessControlAllowHeaders: ["*"]
      """),
  CHAIN(
      "chain",
      "Chain",
      "protected",
      """
      # Several profiles under one name. Each one must be a Route Profile of this Workstation.
      chain:
        middlewares:
          - office-only
          - team-login
      """);

  /** In the address, such as {@code ?example=basic-auth}. */
  final String key;

  final String label;

  final String profile;

  final String text;

  RouteProfileExample(String key, String label, String profile, String text) {
    this.key = key;
    this.label = label;
    this.profile = profile;
    this.text = text;
  }

  static Optional<RouteProfileExample> of(String key) {
    return Arrays.stream(values()).filter(example -> example.key.equals(key)).findFirst();
  }
}
