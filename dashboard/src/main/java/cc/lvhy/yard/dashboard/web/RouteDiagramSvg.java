package cc.lvhy.yard.dashboard.web;

import cc.lvhy.yard.dashboard.services.Health;
import cc.lvhy.yard.dashboard.services.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xmlet.htmlapifaster.CustomElement;
import org.xmlet.htmlapifaster.Element;
import org.xmlet.htmlapifaster.FlowContent;
import org.xmlet.htmlapifaster.Svg;

/**
 * Draws a {@link RouteDiagram} as one inline SVG that runs from the top down: the browser, the
 * Traefik entry points, the Route with its Route Profiles and load balancing, then the Instances.
 * The shapes carry classes, and app.css gives them the colors of the theme, so the dark theme
 * works. Text in a shape is short. The facts next to it hold the full values.
 */
final class RouteDiagramSvg {

  private static final int WIDTH = 600;

  private static final String LOCK_SHACKLE = "M7 11V7a5 5 0 0 1 10 0v4";
  private static final String LOCK_OPEN_SHACKLE = "M7 11V7a5 5 0 0 1 9.9-1";
  private static final String KEY =
      "M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1"
          + " 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z";

  private final RouteDiagram diagram;

  private final Node root = new Node("g", null);

  private int height;

  private RouteDiagramSvg(RouteDiagram diagram) {
    this.diagram = diagram;
  }

  /** One drawn element, kept until the height is known, because the SVG tag comes first. */
  private static final class Node {
    final String tag;
    final String text;
    final Map<String, String> attrs = new LinkedHashMap<>();
    final List<Node> children = new ArrayList<>();

    Node(String tag, String text) {
      this.tag = tag;
      this.text = text;
    }

    Node attr(String name, Object value) {
      attrs.put(name, String.valueOf(value));
      return this;
    }
  }

  /** The SVG element of {@code diagram}, written into {@code parent}. */
  static void draw(FlowContent<?, ?> parent, RouteDiagram diagram) {
    RouteDiagramSvg svg = new RouteDiagramSvg(diagram);
    svg.layout();
    Svg<?> element = parent.svg();
    element
        .attrClass("route-diagram-svg")
        .addAttr("viewBox", "0 0 " + WIDTH + " " + svg.height)
        .addAttr("role", "img")
        .addAttr("aria-labelledby", "route-diagram-title route-diagram-desc");
    CustomElement<?> title = element.custom("title").attrId("route-diagram-title");
    title.text("Route Diagram of " + diagram.service());
    title.__();
    CustomElement<?> desc = element.custom("desc").attrId("route-diagram-desc");
    desc.text(description(diagram));
    desc.__();
    svg.root.children.forEach(child -> emit(element, child));
    element.__();
  }

  private static void emit(Element<?, ?> parent, Node node) {
    CustomElement<?> element = parent.custom(node.tag);
    node.attrs.forEach((name, value) -> element.addAttr(name, Layout.attr(value)));
    if (node.text != null) {
      element.text(node.text);
    }
    node.children.forEach(child -> emit(element, child));
    element.__();
  }

  /** A sentence for a screen reader. The facts next to the diagram say the rest. */
  static String description(RouteDiagram d) {
    StringBuilder text = new StringBuilder();
    if (d.noRoute() != null) {
      text.append(d.noRoute());
    } else {
      text.append("A request for ")
          .append(d.hostname())
          .append(" reaches Traefik on ")
          .append(d.viewer())
          .append(" over ")
          .append(
              d.httpServes() && d.httpsServes()
                  ? "HTTP or HTTPS"
                  : d.httpsServes() ? "HTTPS" : "HTTP")
          .append(". It passes ")
          .append(d.profiles().size())
          .append(d.profiles().size() == 1 ? " Route Profile" : " Route Profiles")
          .append(", then goes to an Instance over ")
          .append(d.mutualTls() ? "mutual TLS" : "plain HTTP")
          .append(".");
    }
    long instances = d.groups().stream().mapToLong(group -> group.members().size()).sum();
    long routed =
        d.groups().stream()
            .flatMap(group -> group.members().stream())
            .filter(RouteDiagram.Member::routed)
            .count();
    return text + " The Service has " + instances + " Instances. " + routed + " are in the Route.";
  }

  // ---- layout ----

  private void layout() {
    int y = 12;
    y = client(y);
    boolean route = diagram.noRoute() == null;
    int zoneTop = y + 58;
    int lastBoxBottom = traefik(zoneTop, route);
    int next = lastBoxBottom + 14;
    int instancesTop;
    int trunkStart = lastBoxBottom;
    if (route) {
      hop(next);
      instancesTop = next + 36;
    } else {
      instancesTop = next + 12;
    }
    int instancesBottom = instances(instancesTop, route, trunkStart);
    int bottom = yard(instancesBottom + 18);
    height = bottom + 12;
  }

  private int client(int y) {
    add(rect(180, y, 240, 52, "rd-node"));
    add(text(300, y + 22, "Browser", "rd-b", "middle"));
    String host = diagram.hostname() == null ? "no hostname here" : fit(diagram.hostname(), 28);
    add(text(300, y + 40, host, diagram.hostname() == null ? "rd-m rd-mute" : "rd-m", "middle"));
    return y + 52;
  }

  /**
   * @return the bottom of the last box in the zone
   */
  private int traefik(int top, boolean route) {
    boolean httpWire = diagram.httpServes();
    boolean httpsWire = diagram.httpsServes();
    int entries = top + 36;
    // From the browser to the two entry points.
    wire(235, top - 58, entries, httpWire ? "rd-wire rd-plain" : "rd-wire rd-wire-off");
    wire(365, top - 58, entries, httpsWire ? "rd-wire" : "rd-wire rd-wire-off");
    hopLabel(
        225,
        top - 24,
        "end",
        httpWire ? "plain HTTP" : diagram.http() == null ? "no Traefik" : httpOffReason(route),
        httpWire ? HopKind.PLAIN : HopKind.OFF);
    hopLabel(
        375,
        top - 24,
        "start",
        httpsWire
            ? diagram.clientCertificate() ? "TLS, Client Certificate" : "TLS"
            : diagram.https() == null ? "HTTPS is off" : "no Route",
        httpsWire ? HopKind.TLS : HopKind.OFF);

    Node zone = rect(20, top, 560, 0, "rd-zone");
    add(zone);
    add(text(40, top + 20, "Traefik on " + fit(diagram.viewer(), 22), "rd-b", "start"));
    entry(40, entries, "HTTP", diagram.http(), httpWire);
    entry(330, entries, "HTTPS", diagram.https(), httpsWire);
    int y = entries + 66;
    if (!route) {
      List<String> lines = wrap(diagram.noRoute(), 66);
      int boxHeight = 12 + lines.size() * 18;
      y += 14;
      add(rect(40, y, 520, boxHeight, "rd-node rd-dashed"));
      int lineY = y + 22;
      for (String line : lines) {
        add(text(56, lineY, line, "rd-t", "start"));
        lineY += 18;
      }
      y += boxHeight;
      zone.attr("height", y + 14 - top);
      return y;
    }
    wire(235, y, y + 24, httpWire ? "rd-wire rd-plain" : "rd-wire rd-wire-off");
    wire(365, y, y + 24, httpsWire ? "rd-wire" : "rd-wire rd-wire-off");
    y += 24;
    add(rect(40, y, 520, 52, "rd-node"));
    add(text(56, y + 22, "Route", "rd-b", "start"));
    if (diagram.accessLog()) {
      add(text(544, y + 22, "access log on", "rd-s rd-mute", "end"));
    }
    add(text(56, y + 41, fit(diagram.hostname(), 60), "rd-m", "start"));
    y += 52;
    wire(300, y, y + 22, "rd-wire");
    y += 22;
    y = profiles(y);
    wire(300, y, y + 22, "rd-wire");
    y += 22;
    y = balancing(y);
    zone.attr("height", y + 14 - top);
    return y;
  }

  private String httpOffReason(boolean route) {
    if (!route) {
      return "no Route";
    }
    return diagram.clientCertificate() ? "needs HTTPS" : "off";
  }

  private void entry(int x, int y, String name, RouteDiagram.Listener listener, boolean serves) {
    boolean on = listener != null;
    add(rect(x, y, 230, 66, on ? "rd-node" : "rd-node rd-dashed"));
    add(text(x + 14, y + 22, name, on ? "rd-b" : "rd-b rd-mute", "start"));
    if (on) {
      add(text(x + 216, y + 22, serves ? "serves the Route" : "no Route", "rd-s rd-mute", "end"));
      add(text(x + 14, y + 41, listener.address(), "rd-m", "start"));
      add(text(x + 14, y + 57, listener.reach(), "rd-s rd-mute", "start"));
    } else {
      add(text(x + 14, y + 41, "off", "rd-s rd-mute", "start"));
    }
  }

  private int profiles(int y) {
    List<RouteProfileSummary> profiles = diagram.profiles();
    if (profiles.isEmpty()) {
      add(rect(40, y, 520, 32, "rd-node"));
      add(text(56, y + 21, "No Route Profiles", "rd-t rd-mute", "start"));
      return y + 32;
    }
    int top = y;
    int boxIndex = root.children.size();
    int cursor = y + 12;
    for (RouteProfileSummary profile : profiles) {
      add(text(56, cursor + 12, fit(profile.name(), 40), "rd-b", "start"));
      if (profile.missing()) {
        add(
            text(
                544,
                cursor + 12,
                "missing: Traefik refuses this Route",
                "rd-s rd-risk-text",
                "end"));
        cursor += 26;
        continue;
      }
      cursor += 22;
      int x = 56;
      for (RouteProfileSummary.Middleware middleware : profile.middlewares()) {
        String label = fit(middleware.type(), 22);
        int width = (int) (label.length() * 7.4) + 20 + (middleware.authentication() ? 18 : 0);
        if (x + width > 548 && x > 56) {
          x = 56;
          cursor += 30;
        }
        chip(x, cursor, width, label, middleware.authentication());
        x += width + 8;
      }
      if (profile.middlewares().isEmpty()) {
        add(text(56, cursor + 16, "no middleware", "rd-s rd-mute", "start"));
      }
      cursor += 38;
    }
    int bottom = cursor + 2;
    root.children.add(boxIndex, rect(40, top, 520, bottom - top, "rd-node"));
    return bottom;
  }

  private int balancing(int y) {
    List<String> lines = wrap(diagram.balancing(), 66);
    int heightOfBox = 34 + lines.size() * 17;
    add(rect(40, y, 520, heightOfBox, "rd-node"));
    add(text(56, y + 22, "Load balancing", "rd-b", "start"));
    int lineY = y + 40;
    for (String line : lines) {
      add(text(56, lineY, line, "rd-t", "start"));
      lineY += 17;
    }
    return y + heightOfBox;
  }

  private void hop(int y) {
    boolean mutual = diagram.mutualTls();
    String label =
        (diagram.remote() ? "Across the LAN: " : "Traefik to Instances: ")
            + (mutual ? "mutual TLS" : "plain HTTP");
    lock(72, y + 6, !mutual);
    add(text(94, y + 19, label, mutual ? "rd-s" : "rd-s rd-risk-text", "start"));
  }

  private int instances(int top, boolean route, int trunkStart) {
    List<RouteDiagram.Group> groups = diagram.groups();
    String title = diagram.remote() ? "Workstation " + diagram.home() : "Instances";
    Node zone = rect(20, top, 560, 0, diagram.remote() ? "rd-zone rd-dashed" : "rd-zone");
    add(zone);
    // The trunk runs down at x=60, so the title starts to its right.
    add(text(route ? 80 : 40, top + 22, title, "rd-b", "start"));
    int y = top + 34;
    if (groups.isEmpty()) {
      add(text(40, y + 14, "No Instance Source yet.", "rd-t rd-mute", "start"));
      zone.attr("height", y + 30 - top);
      return y + 24;
    }
    int firstBranch = -1;
    int lastBranch = -1;
    List<Node> branches = new ArrayList<>();
    for (RouteDiagram.Group group : groups) {
      int rows = Math.max(1, group.members().size());
      int boxHeight = 32 + rows * 28 + 6;
      boolean dim = route && group.use() != RouteDiagram.Use.IN_ROUTE;
      add(rect(80, y, 480, boxHeight, dim ? "rd-node rd-dashed" : "rd-node"));
      add(text(96, y + 21, fit(group.label(), 46), "rd-b", "start"));
      String use =
          switch (group.use()) {
            case NONE -> null;
            case IN_ROUTE -> "in the Route";
            case WAITING -> "waiting";
            case OUT_OF_RANGE -> "skipped here";
          };
      if (use != null) {
        add(text(544, y + 21, use, "rd-s rd-mute", "end"));
      }
      int rowY = y + 32;
      if (group.members().isEmpty()) {
        add(text(96, rowY + 18, "no Instance now", "rd-s rd-mute", "start"));
      }
      for (RouteDiagram.Member member : group.members()) {
        member(rowY, member, route);
        rowY += 28;
      }
      if (route) {
        int branchY = y + 16;
        firstBranch = firstBranch < 0 ? branchY : firstBranch;
        lastBranch = branchY;
        boolean used = group.use() == RouteDiagram.Use.IN_ROUTE;
        Node branch = path("M60 " + branchY + "H80", used ? "rd-wire" : "rd-wire rd-wire-off");
        branches.add(branch);
      }
      y += boxHeight + 10;
    }
    if (route && lastBranch >= 0) {
      add(path("M60 " + trunkStart + "V" + lastBranch, "rd-wire"));
      branches.forEach(this::add);
    }
    zone.attr("height", y + 4 - top);
    return y + 4;
  }

  private void member(int y, RouteDiagram.Member member, boolean route) {
    boolean dim = route && !member.routed();
    String tone = dim ? " rd-dim" : "";
    if (member.routed()) {
      add(rect(88, y + 2, 3, 22, "rd-in"));
    }
    add(circle(106, y + 13, 5, healthClass(member.health())));
    add(text(118, y + 17, member.health().value(), "rd-s" + tone, "start"));
    add(text(184, y + 17, fit(member.address(), 28), "rd-m" + tone, "start"));
    add(text(544, y + 17, member.place().label, "rd-s rd-mute", "end"));
  }

  private static String healthClass(Health health) {
    return switch (health) {
      case PASSING -> "rd-ok";
      case WARNING -> "rd-warn";
      case CRITICAL -> "rd-bad";
    };
  }

  private int yard(int top) {
    List<String> lines = new ArrayList<>();
    String monoLine = null;
    String risk = null;
    Scope scope = diagram.scope();
    if (scope == null) {
      lines.add("Not published: no Workstation has a hostname for this Service.");
    } else if (scope == Scope.LOCAL) {
      lines.add("Local Scope: only " + diagram.home() + " has a hostname for this Service.");
    } else if (diagram.remote()) {
      lines.add("Yard Scope: " + diagram.home() + " shares this Service with the Yard.");
      lines.add("This Workstation sends requests straight to its Instances.");
      monoLine = diagram.yardHostname();
    } else {
      lines.add("Yard Scope: every Workstation in the Yard gets a hostname for it.");
      monoLine = diagram.yardHostname();
      if (diagram.skippedByYard() > 0) {
        risk =
            diagram.skippedByYard()
                + (diagram.skippedByYard() == 1 ? " Instance is" : " Instances are")
                + " at a loopback or Docker address. Other Workstations skip "
                + (diagram.skippedByYard() == 1 ? "it." : "them.");
      }
    }
    int rows = lines.size() + (monoLine == null ? 0 : 1) + (risk == null ? 0 : 1);
    int boxHeight = 20 + rows * 20;
    add(rect(20, top, 560, boxHeight, "rd-band"));
    int y = top + 24;
    for (String line : lines) {
      add(text(40, y, fit(line, 74), "rd-t", "start"));
      y += 20;
    }
    if (monoLine != null) {
      add(text(40, y, fit(monoLine, 66), "rd-m", "start"));
      y += 20;
    }
    if (risk != null) {
      add(text(40, y, fit(risk, 74), "rd-t rd-risk-text", "start"));
    }
    return top + boxHeight;
  }

  // ---- shapes ----

  private enum HopKind {
    PLAIN,
    TLS,
    OFF
  }

  /** A short label beside a wire, with a lock for TLS and an open lock for plain HTTP. */
  private void hopLabel(int x, int y, String anchor, String label, HopKind kind) {
    String cls =
        switch (kind) {
          case PLAIN -> "rd-s rd-risk-text";
          case TLS -> "rd-s";
          case OFF -> "rd-s rd-mute";
        };
    int textWidth = (int) (label.length() * 6.4);
    boolean end = anchor.equals("end");
    int iconX = kind == HopKind.OFF ? -100 : end ? x - textWidth - 20 : x;
    int textX = kind == HopKind.OFF || end ? x : x + 20;
    if (kind != HopKind.OFF) {
      lock(iconX, y - 10, kind == HopKind.PLAIN);
    }
    add(text(textX, y + 3, label, cls, anchor));
  }

  /** A padlock about 14 units wide, at {@code x}, {@code y}. */
  private void lock(int x, int y, boolean open) {
    Node group = new Node("g", null).attr("transform", "translate(" + x + " " + y + ") scale(0.6)");
    group.attr("class", open ? "rd-icon rd-risk-icon" : "rd-icon");
    group.children.add(
        new Node("rect", null)
            .attr("x", 3)
            .attr("y", 11)
            .attr("width", 18)
            .attr("height", 11)
            .attr("rx", 2));
    group.children.add(new Node("path", null).attr("d", open ? LOCK_OPEN_SHACKLE : LOCK_SHACKLE));
    add(group);
  }

  private void chip(int x, int y, int width, String label, boolean authentication) {
    add(rect(x, y, width, 24, authentication ? "rd-chip rd-chip-auth" : "rd-chip"));
    int textX = x + 10;
    if (authentication) {
      Node key =
          new Node("g", null)
              .attr("transform", "translate(" + (x + 8) + " " + (y + 5) + ") scale(0.6)");
      key.attr("class", "rd-icon");
      key.children.add(new Node("path", null).attr("d", KEY));
      key.children.add(new Node("circle", null).attr("cx", 16.5).attr("cy", 7.5).attr("r", 0.5));
      add(key);
      textX += 18;
    }
    add(text(textX, y + 16, label, "rd-m rd-s", "start"));
  }

  /** A wire with a chevron at its lower end. */
  private void wire(int x, int from, int to, String cls) {
    add(path("M" + x + " " + from + "V" + to, cls));
    add(
        path(
            "M" + (x - 4) + " " + (to - 6) + "L" + x + " " + to + "L" + (x + 4) + " " + (to - 6),
            cls));
  }

  private void add(Node node) {
    root.children.add(node);
  }

  private static Node rect(int x, int y, int width, int height, String cls) {
    return new Node("rect", null)
        .attr("x", x)
        .attr("y", y)
        .attr("width", width)
        .attr("height", height)
        .attr("rx", 8)
        .attr("class", cls);
  }

  private static Node circle(int cx, int cy, int r, String cls) {
    return new Node("circle", null).attr("cx", cx).attr("cy", cy).attr("r", r).attr("class", cls);
  }

  private static Node path(String d, String cls) {
    return new Node("path", null).attr("d", d).attr("class", cls);
  }

  private static Node text(int x, int y, String text, String cls, String anchor) {
    Node node = new Node("text", text).attr("x", x).attr("y", y).attr("class", cls);
    if (!anchor.equals("start")) {
      node.attr("text-anchor", anchor);
    }
    return node;
  }

  /** At most {@code max} characters, with an ellipsis where the text is cut. */
  static String fit(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max - 1) + "…";
  }

  /** Lines of at most {@code width} characters, broken at spaces. A long word is cut. */
  static List<String> wrap(String text, int width) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      String piece = fit(word, width);
      if (line.length() > 0 && line.length() + 1 + piece.length() > width) {
        lines.add(line.toString());
        line.setLength(0);
      }
      if (line.length() > 0) {
        line.append(' ');
      }
      line.append(piece);
    }
    if (line.length() > 0) {
      lines.add(line.toString());
    }
    return lines;
  }
}
