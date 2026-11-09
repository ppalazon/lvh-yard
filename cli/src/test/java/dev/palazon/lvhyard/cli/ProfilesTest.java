package dev.palazon.lvhyard.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfilesTest extends YardTestSupport {

  @Test
  void profilesListsTheRouteProfilesWithTheirTypesAndTheServicesThatUseThem() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles",
        200,
        """
        [{"name": "behind-https", "type": "headers", "services": ["api", "web"]},
         {"name": "team-login", "type": "basicAuth", "services": []}]
        """);

    Run run = yard("profiles");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            behind-https: headers, used by api, web
            team-login: basicAuth, not used
            """);
  }

  @Test
  void profilesSaysWhenThereAreNone() {
    dashboard.answer("GET", "/api/workstation/route-profiles", 200, "[]");

    Run run = yard("profiles");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("No Route Profiles.\n");
  }

  @Test
  void profilesShowPrintsTheProfileInTraefiksFileFormat() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/team-login",
        200,
        """
        {"name": "team-login", "type": "basicAuth", "services": ["web"],
         "definition": {"basicAuth": {"users": ["ana:$2y$05$abc"], "removeHeader": true}}}
        """);

    Run run = yard("profiles", "show", "team-login");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out())
        .isEqualTo(
            """
            http:
              middlewares:
                team-login:
                  basicAuth:
                    users:
                      - ana:$2y$05$abc
                    removeHeader: true
            """);
  }

  @Test
  void profilesShowSaysWhenTheProfileDoesNotExist() {
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/nope",
        404,
        """
        {"title": "Not Found", "status": 404,
         "detail": "This Workstation has no Route Profile nope."}
        """);

    Run run = yard("profiles", "show", "nope");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).isEqualTo("This Workstation has no Route Profile nope.\n");
  }

  @Test
  void profilesSetSendsEveryProfileOfTheFileAsOneSet(@TempDir Path dir) throws Exception {
    dashboard.answer("PUT", "/api/workstation/route-profiles", 200, "[]");
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(
        file,
        """
        http:
          middlewares:
            office-only:
              ipAllowList:
                sourceRange: ["192.168.1.0/24"]
            protected:
              chain:
                middlewares: [office-only]
        """);

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profiles office-only, protected.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/route-profiles",
        """
        {"office-only": {"ipAllowList": {"sourceRange": ["192.168.1.0/24"]}},
         "protected": {"chain": {"middlewares": ["office-only"]}}}
        """);
  }

  @Test
  void profilesSetTakesWhatProfilesShowPrints(@TempDir Path dir) throws Exception {
    String definition =
        """
        {"headers": {"customRequestHeaders": {"X-Forwarded-Proto": "https", "X-Number": "100",
                                              "X-Yes": "true", "X-On": "on", "X-Empty": "",
                                              "X-Colon": "a: b", "X-Hash": "#1", "X-Null": "null"},
                     "sslRedirect": true, "accessControlMaxAge": 100, "hostsProxyHeaders": []}}
        """;
    dashboard.answer(
        "GET",
        "/api/workstation/route-profiles/behind-https",
        200,
        "{\"name\": \"behind-https\", \"type\": \"headers\", \"services\": [], \"definition\": "
            + definition
            + "}");
    dashboard.answer("PUT", "/api/workstation/route-profiles", 200, "[]");
    Path file = dir.resolve("behind-https.yaml");
    Files.writeString(file, yard("profiles", "show", "behind-https").out());

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profile behind-https.\n");
    dashboard.verifyCalled(
        "PUT", "/api/workstation/route-profiles", "{\"behind-https\": " + definition + "}");
  }

  @Test
  void profilesSetPrintsEachReasonWhenTheDashboardRefusesTheSet(@TempDir Path dir)
      throws Exception {
    dashboard.answer(
        "PUT",
        "/api/workstation/route-profiles",
        400,
        """
        {"title": "Bad Request", "status": 400,
         "detail": "from-file reads its users from usersFile. typo has the type basicAut.",
         "problems": [{"profile": "from-file", "reason": "from-file reads its users from usersFile."},
                      {"profile": "typo", "reason": "typo has the type basicAut."}]}
        """);
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(
        file,
        """
        http:
          middlewares:
            from-file:
              basicAuth:
                usersFile: /etc/users
            typo:
              basicAut: {}
        """);

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.out()).isEmpty();
    assertThat(run.err())
        .isEqualTo(
            """
            from-file reads its users from usersFile.
            typo has the type basicAut.
            """);
  }

  @Test
  void profilesSetRefusesAFileWithoutHttpMiddlewares(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(file, "gzip:\n  compress: {}\n");

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("http.middlewares");
    dashboard.verifyNotCalled();
  }

  @Test
  void profilesSetRefusesAFileThatIsNotYaml(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("profiles.yaml");
    Files.writeString(file, "http:\n  middlewares: [unclosed\n");

    Run run = yard("profiles", "set", file.toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).startsWith("The file is not valid YAML").contains("line");
  }

  @Test
  void profilesSetSaysWhenTheFileIsMissing(@TempDir Path dir) {
    Run run = yard("profiles", "set", dir.resolve("missing.yaml").toString());

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).contains("missing.yaml");
  }

  @Test
  void profilesRemoveRemovesTheProfile() {
    dashboard.answer("DELETE", "/api/workstation/route-profiles/gzip", 204, null);

    Run run = yard("profiles", "remove", "gzip");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Removed the Route Profile gzip.\n");
    dashboard.verifyCalled("DELETE", "/api/workstation/route-profiles/gzip");
  }

  @Test
  void profilesRemovePrintsTheServicesAndChainsThatUseTheProfile() {
    dashboard.answer(
        "DELETE",
        "/api/workstation/route-profiles/login",
        409,
        """
        {"title": "Conflict", "status": 409,
         "detail": "login is in use, so it cannot be removed. The Publications of these Services name it: web. These chains name it: protected.",
         "services": ["web"], "chains": ["protected"]}
        """);

    Run run = yard("profiles", "remove", "login");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .isEqualTo(
            "login is in use, so it cannot be removed. The Publications of these Services name"
                + " it: web. These chains name it: protected.\n");
  }

  @Test
  void profilesSetReadsStandardInputForADash() throws Exception {
    dashboard.answer("PUT", "/api/workstation/route-profiles", 200, "[]");

    Run run =
        yardWithInput(
            """
            http:
              middlewares:
                gzip:
                  compress: {}
            """,
            "--dashboard",
            dashboard.url().toString(),
            "profiles",
            "set",
            "-");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profile gzip.\n");
    dashboard.verifyCalled(
        "PUT", "/api/workstation/route-profiles", "{\"gzip\": {\"compress\": {}}}");
  }

  @Test
  void profilesEditSendsTheChangedProfile() throws Exception {
    answerTeamProfile();
    answerTeamProfileSaved();

    Run run =
        yardWithEditor(
            text -> text.replace("X-Team: a", "X-Team: b"), "", "profiles", "edit", "team");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("Saved the Route Profile team.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/route-profiles/team",
        "{\"headers\": {\"customRequestHeaders\": {\"X-Team\": \"b\"}}}");
  }

  @Test
  void profilesEditDoesNothingWhenTheTextIsTheSame() throws Exception {
    answerTeamProfile();

    Run run = yardWithEditor(text -> text, "", "profiles", "edit", "team");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).isEqualTo("No change to the Route Profile team.\n");
    dashboard.verifyNotCalled("PUT", "/api/workstation/route-profiles/team");
  }

  @Test
  void profilesEditRefusesAnotherNameAndKeepsTheTextWhenAskedNotToEditAgain() throws Exception {
    answerTeamProfile();

    Run run =
        yardWithEditor(text -> text.replace("team:", "crew:"), "n\n", "profiles", "edit", "team");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .startsWith("The file must hold only the Route Profile team. It holds crew.\n")
        .contains("Edit again? [Y/n] ")
        .contains("Did not save the Route Profile team. Your text is in ");
    dashboard.verifyNotCalled("PUT", "/api/workstation/route-profiles/team");
  }

  @Test
  void profilesEditRefusesASecondProfile() throws Exception {
    answerTeamProfile();

    Run run =
        yardWithEditor(
            text -> text + "    gzip:\n      compress: {}\n", "n\n", "profiles", "edit", "team");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err())
        .startsWith("The file must hold only the Route Profile team. It holds team, gzip.\n");
    dashboard.verifyNotCalled("PUT", "/api/workstation/route-profiles/team");
  }

  @Test
  void profilesEditOffersTheSameTextAgainAfterARefusal() throws Exception {
    answerTeamProfile();
    answerTeamProfileSaved();

    // The first run renames the profile. The second finds that name, so the text is the same.
    Run run =
        yardWithEditor(
            text ->
                text.contains("crew:")
                    ? text.replace("crew:", "team:").replace("X-Team: a", "X-Team: b")
                    : text.replace("team:", "crew:"),
            "\n",
            "profiles",
            "edit",
            "team");

    assertThat(run.exitCode()).isZero();
    assertThat(run.out()).endsWith("Saved the Route Profile team.\n");
    dashboard.verifyCalled(
        "PUT",
        "/api/workstation/route-profiles/team",
        "{\"headers\": {\"customRequestHeaders\": {\"X-Team\": \"b\"}}}");
  }

  @Test
  void profilesEditPrintsEachReasonWhenTheDashboardRefusesTheProfile() throws Exception {
    answerTeamProfile();
    dashboard.answer(
        "PUT",
        "/api/workstation/route-profiles/team",
        400,
        """
        {"title": "Bad Request", "status": 400,
         "detail": "team has the type headerz.",
         "problems": [{"profile": "team", "reason": "team has the type headerz."}]}
        """);

    Run run =
        yardWithEditor(
            text -> text.replace("headers:", "headerz:"), "n\n", "profiles", "edit", "team");

    assertThat(run.exitCode()).isNotZero();
    assertThat(run.err()).startsWith("team has the type headerz.\nEdit again? [Y/n] ");
  }
}
