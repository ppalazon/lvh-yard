package cc.lvhy.yard.dashboard.web;

/** Middleware examples for reference, not editor presets. */
enum RouteProfileExample {
  CLIENT_CERTIFICATE(
      "Client Certificate",
      "Require a Yard CA Client Certificate over HTTPS.",
      """
      clientCertificate: {}
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/tls/tls-options/#client-authentication-mtls"),
  BASIC_AUTH(
      "Basic auth",
      "Require a user and password. Replace the placeholder with htpasswd -nB alice output."
          + " HTTP does not encrypt credentials; use a trusted network or HTTPS.",
      """
      basicAuth:
        users:
          # Replace this whole line with a real user and bcrypt hash.
          - "alice:REPLACE_WITH_BCRYPT_HASH"
        removeHeader: true
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/basicauth/"),
  IP_ALLOW_LIST(
      "IP allow list",
      "Allow only selected client IPs. Replace the documentation range with your network."
          + " This checks the direct peer address, not X-Forwarded-For.",
      """
      ipAllowList:
        sourceRange:
          - "192.0.2.0/24"
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/ipallowlist/"),
  REQUEST_HEADERS(
      "Request headers",
      "Tell the Service the original request used HTTPS. Use only when a trusted HTTPS proxy"
          + " handles all incoming traffic. This does not enable HTTPS; Yard uses HTTP by default.",
      """
      headers:
        customRequestHeaders:
          X-Forwarded-Proto: "https"
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/headers/#adding-headers-to-the-request-and-the-response"),
  SECURITY_HEADERS(
      "Response security headers",
      "Block framing and MIME sniffing. The Service must send correct Content-Type headers"
          + " and must not need to appear in an iframe.",
      """
      headers:
        frameDeny: true
        contentTypeNosniff: true
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/headers/#configuration-options"),
  CORS(
      "CORS",
      "Allow browser calls from one origin. Replace the origin, including its port if needed."
          + " Traefik answers preflight requests. CORS is not authentication.",
      """
      headers:
        accessControlAllowOriginList:
          - "http://app.example.com"
        accessControlAllowMethods: [GET, POST, OPTIONS]
        accessControlAllowHeaders: [Content-Type]
        addVaryHeader: true
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/headers/#cors-headers"),
  FORWARD_AUTH(
      "Forward auth",
      "Ask an auth service before forwarding requests. Replace the endpoint with one every"
          + " Workstation can reach. It must return 2xx only for allowed requests.",
      """
      forwardAuth:
        address: "https://auth.example.com/verify"
        authRequestHeaders: [Cookie, Authorization]
        authResponseHeaders: [X-Auth-User]
        maxResponseBodySize: 65536
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/forwardauth/"),
  CHAIN(
      "Chain",
      "Apply office-only, then team-login. Both must already exist as Route Profiles"
          + " on this Workstation.",
      """
      chain:
        middlewares:
          - office-only
          - team-login
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/chain/"),
  COMPRESSION(
      "Compression",
      "Compress responses over 1 KiB when the client accepts compression."
          + " Leave event streams uncompressed.",
      """
      compress:
        minResponseBodyBytes: 1024
        excludedContentTypes:
          - "text/event-stream"
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/compress/"),
  REDIRECT_REGEX(
      "URL redirect",
      "Redirect browsers to another host. Replace both hosts; the target needs its own Route."
          + " This does not rewrite backend paths.",
      """
      redirectRegex:
        regex: '^http://old[.]example[.]com/(.*)$'
        replacement: 'http://app.example.com/${1}'
        permanent: false
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/redirectregex/"),
  CONTENT_TYPE(
      "Content type",
      "Detect a missing response Content-Type. Prefer fixing the Service to send it."
          + " This has no effect if another middleware sets the header.",
      """
      contentType: {}
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/contenttype/"),
  BUFFERING(
      "Buffering",
      "Buffer bodies up to 10 MiB, using disk above 1 MiB. Larger requests get 413;"
          + " larger responses get 500. These limits are not suitable for streaming.",
      """
      buffering:
        maxRequestBodyBytes: 10485760
        memRequestBodyBytes: 1048576
        maxResponseBodyBytes: 10485760
        memResponseBodyBytes: 1048576
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/buffering/"),
  CIRCUIT_BREAKER(
      "Circuit breaker",
      "Return 503 when over 25% of responses are 5xx. Tune the threshold for the Service."
          + " Each Route has its own state; middleware order affects what it sees.",
      """
      circuitBreaker:
        expression: "ResponseCodeRatio(500, 600, 0, 600) > 0.25"
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/circuitbreaker/"),
  RETRY(
      "Retry",
      "Retry network failures with a short delay. Use only when repeating a request is safe;"
          + " retries can duplicate writes.",
      """
      retry:
        attempts: 3
        initialInterval: "100ms"
        retryNonIdempotentMethod: false
      """,
      "https://doc.traefik.io/traefik/v3.7/reference/routing-configuration/http/middlewares/retry/");

  final String label;

  final String description;

  final String text;

  final String reference;

  RouteProfileExample(String label, String description, String text, String reference) {
    this.label = label;
    this.description = description;
    this.text = text;
    this.reference = reference;
  }
}
