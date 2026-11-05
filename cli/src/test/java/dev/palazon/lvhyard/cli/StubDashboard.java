package dev.palazon.lvhyard.cli;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * A stand-in for the dashboard, on a free loopback port. It answers only what the dashboard's
 * committed {@code openapi.yaml} describes: a stub for a path, method or status the contract lacks,
 * or with a body that does not fit its schema, fails the test. So the CLI's tests break when the
 * API they rely on changes.
 */
final class StubDashboard implements AutoCloseable {

  private static final Path CONTRACT = Path.of("../dashboard/openapi.yaml");

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  private final JsonNode contract = YAMLMapper.builder().build().readTree(CONTRACT.toFile());

  private final WireMockServer server =
      new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

  StubDashboard() {
    server.start();
  }

  URI url() {
    return URI.create(server.baseUrl());
  }

  /**
   * Answers {@code method} on {@code path}, such as {@code /api/services?workstation=ws-b}, with
   * {@code status} and the JSON {@code body}, or with no body when it is {@code null}. Errors
   * answer as Problem Details.
   */
  void answer(String method, String path, int status, String body) {
    URI uri = URI.create(path);
    JsonNode response = checkedResponse(method, uri, status);
    var answer = WireMock.aResponse().withStatus(status);
    if (body != null) {
      String mediaType = status >= 400 ? "application/problem+json" : "application/json";
      JsonNode schema = response.path("content").path(mediaType).path("schema");
      if (schema.isMissingNode()) {
        throw new AssertionError(
            method + " " + path + " " + status + " has no " + mediaType + " body in " + CONTRACT);
      }
      List<String> errors = new ArrayList<>();
      checkFits(JSON.readTree(body), schema, "body", errors);
      if (!errors.isEmpty()) {
        throw new AssertionError(
            method + " " + path + " " + status + " does not fit " + CONTRACT + ": " + errors);
      }
      answer.withHeader("Content-Type", mediaType).withBody(body);
    }
    var request = WireMock.request(method, WireMock.urlPathEqualTo(uri.getRawPath()));
    if (uri.getQuery() != null) {
      for (String pair : uri.getQuery().split("&")) {
        String[] nameAndValue = pair.split("=", 2);
        request.withQueryParam(nameAndValue[0], WireMock.equalTo(nameAndValue[1]));
      }
    }
    server.stubFor(request.willReturn(answer));
  }

  /**
   * Checks that the dashboard was asked {@code method} on {@code path} with exactly this JSON body,
   * and that the body fits the request the contract describes.
   */
  void verifyCalled(String method, String path, String body) {
    JsonNode schema =
        pathItem(path)
            .path(method.toLowerCase())
            .path("requestBody")
            .path("content")
            .path("application/json")
            .path("schema");
    if (schema.isMissingNode()) {
      throw new AssertionError(method + " " + path + " takes no JSON body in " + CONTRACT);
    }
    List<String> errors = new ArrayList<>();
    checkFits(JSON.readTree(body), schema, "request", errors);
    if (!errors.isEmpty()) {
      throw new AssertionError(
          method + " " + path + " request does not fit " + CONTRACT + ": " + errors);
    }
    server.verify(called(method, path).withRequestBody(WireMock.equalToJson(body)));
  }

  /** Checks that the dashboard was asked {@code method} on {@code path}. */
  void verifyCalled(String method, String path) {
    server.verify(called(method, path));
  }

  void verifyNotCalled() {
    server.verify(0, WireMock.anyRequestedFor(WireMock.anyUrl()));
  }

  private static RequestPatternBuilder called(String method, String path) {
    return new RequestPatternBuilder(
        RequestMethod.fromString(method), WireMock.urlPathEqualTo(path));
  }

  @Override
  public void close() {
    server.stop();
  }

  /**
   * The contract's response for {@code method} on {@code uri} with {@code status}, or a failure if
   * it has none.
   */
  private JsonNode checkedResponse(String method, URI uri, int status) {
    JsonNode operation = pathItem(uri.getRawPath()).path(method.toLowerCase());
    if (operation.isMissingNode()) {
      throw new AssertionError(method + " " + uri.getRawPath() + " is not in " + CONTRACT);
    }
    if (uri.getQuery() != null) {
      for (String pair : uri.getQuery().split("&")) {
        String name = pair.split("=", 2)[0];
        boolean declared =
            operation
                .path("parameters")
                .valueStream()
                .anyMatch(parameter ->
                    parameter.path("in").asString().equals("query")
                        && parameter.path("name").asString().equals(name));
        if (!declared) {
          throw new AssertionError(
              method
                  + " "
                  + uri.getRawPath()
                  + " has no query parameter "
                  + name
                  + " in "
                  + CONTRACT);
        }
      }
    }
    JsonNode response = operation.path("responses").path(String.valueOf(status));
    if (response.isMissingNode()) {
      throw new AssertionError(
          method + " " + uri.getRawPath() + " never answers " + status + " in " + CONTRACT);
    }
    return response;
  }

  /**
   * The contract's path whose template matches {@code path}, preferring literal segments over
   * parameters.
   */
  private JsonNode pathItem(String path) {
    String[] segments = path.split("/");
    JsonNode best = null;
    int bestLiterals = -1;
    for (Map.Entry<String, JsonNode> entry : contract.path("paths").properties()) {
      String[] template = entry.getKey().split("/");
      if (template.length != segments.length) {
        continue;
      }
      int literals = 0;
      boolean matches = true;
      for (int i = 0; i < template.length && matches; i++) {
        if (template[i].startsWith("{")) {
          continue;
        }
        matches = template[i].equals(segments[i]);
        literals++;
      }
      if (matches && literals > bestLiterals) {
        best = entry.getValue();
        bestLiterals = literals;
      }
    }
    if (best == null) {
      throw new AssertionError(path + " matches no path in " + CONTRACT);
    }
    return best;
  }

  /**
   * Adds to {@code errors} where {@code value} breaks {@code schema}: a missing required property,
   * a wrong type or a value outside an enum. Enough of JSON Schema for the contract's own schemas.
   */
  private void checkFits(JsonNode value, JsonNode schema, String where, List<String> errors) {
    if (schema.has("$ref")) {
      String name = schema.get("$ref").asString().substring("#/components/schemas/".length());
      checkFits(value, contract.path("components").path("schemas").path(name), where, errors);
      return;
    }
    switch (schema.path("type").asString()) {
      case "object" -> {
        if (!value.isObject()) {
          errors.add(where + " is not an object");
          return;
        }
        schema
            .path("required")
            .valueStream()
            .map(JsonNode::asString)
            .filter(name -> !value.has(name))
            .forEach(name -> errors.add(where + " has no " + name));
        for (Map.Entry<String, JsonNode> property : schema.path("properties").properties()) {
          if (value.has(property.getKey())) {
            checkFits(
                value.get(property.getKey()),
                property.getValue(),
                where + "." + property.getKey(),
                errors);
          }
        }
      }
      case "array" -> {
        if (!value.isArray()) {
          errors.add(where + " is not an array");
          return;
        }
        for (int i = 0; i < value.size(); i++) {
          checkFits(value.get(i), schema.path("items"), where + "[" + i + "]", errors);
        }
      }
      case "string" -> {
        if (!value.isString()) {
          errors.add(where + " is not a string");
        } else if (schema.has("enum")
            && schema
                .get("enum")
                .valueStream()
                .noneMatch(allowed -> allowed.asString().equals(value.asString()))) {
          errors.add(where + " is " + value.asString() + ", not one of " + schema.get("enum"));
        }
      }
      case "integer" -> {
        if (!value.isIntegralNumber()) {
          errors.add(where + " is not an integer");
        }
      }
      default -> {}
    }
  }
}
