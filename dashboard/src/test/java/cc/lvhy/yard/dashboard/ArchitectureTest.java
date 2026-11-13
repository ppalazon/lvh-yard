package cc.lvhy.yard.dashboard;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import cc.lvhy.yard.dashboard.consul.Consul;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The package rules of docs/design.md. A frozen rule has known violations in {@code
 * src/test/resources/archunit_store}, and fails only on a new one. A rule that is not frozen has
 * none.
 */
class ArchitectureTest {

  private static final String BASE = "cc.lvhy.yard.dashboard";

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE);
  }

  /** One slice for each feature package, and one for the classes of the base package. */
  private static final SliceAssignment FEATURE_PACKAGES =
      new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
          String name = javaClass.getPackageName();
          if (!name.startsWith(BASE)) {
            return SliceIdentifier.ignore();
          }
          String rest = name.substring(BASE.length());
          if (rest.isEmpty()) {
            return SliceIdentifier.of("(root)");
          }
          String feature = rest.substring(1);
          int dot = feature.indexOf('.');
          return SliceIdentifier.of(dot < 0 ? feature : feature.substring(0, dot));
        }

        @Override
        public String getDescription() {
          return "feature packages";
        }
      };

  @Test
  void featurePackagesHaveNoCycles() {
    slices().assignedFrom(FEATURE_PACKAGES).should().beFreeOfCycles().check(classes);
  }

  @Test
  void theConsulDockerAndEventsPackagesDependOnNoOtherFeaturePackage() {
    for (String leaf : new String[] {"consul", "docker", "events"}) {
      ArchRule rule =
          noClasses()
              .that()
              .resideInAPackage(BASE + "." + leaf + "..")
              .should()
              .dependOnClassesThat()
              .resideInAnyPackage(otherFeaturePackages(leaf));
      rule.check(classes);
    }
  }

  @Test
  void aFeatureOutsideTheConsulPackageDoesNotSeeTheHttpClientOfTheConsulAdapter() {
    noClasses()
        .that()
        .resideOutsideOfPackage(BASE + ".consul..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web.client..")
        .check(classes);
  }

  @Test
  void aClassAsksForARoleOfConsulNotForAllOfIt() {
    fields()
        .that()
        .haveRawType(Consul.class)
        .should()
        .beDeclaredInClassesThat()
        .resideInAPackage(BASE + ".consul..")
        .because(
            "a class holds ConsulKv, ConsulCatalog, ConsulFederation, ConsulRegistrations or"
                + " ConsulWatching, whichever it uses")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void thePortTypesOfConsulKnowNoJson() {
    noClasses()
        .that()
        .resideInAPackage(BASE + ".consul..")
        .and()
        .haveNameNotMatching(".*ConsulAgent.*")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.fasterxml.jackson..", "tools.jackson..")
        .because("only ConsulAgent turns Consul's JSON into the types of the port")
        .check(classes);
  }

  @Test
  void pureRulesOfTheDomainImportNoSpring() {
    FreezingArchRule.freeze(
            noClasses()
                .that()
                .haveSimpleNameStartingWith("Routing")
                .or()
                .haveSimpleNameStartingWith("ServiceWarning")
                .or()
                .haveSimpleNameStartingWith("OutOfRange")
                .or()
                .haveSimpleNameStartingWith("InstanceRules")
                .or()
                .haveSimpleNameStartingWith("InstanceMapping")
                .or()
                .haveSimpleNameStartingWith("ServiceState")
                .or()
                .haveSimpleNameStartingWith("Health")
                .or()
                .haveSimpleNameStartingWith("Scope")
                .should()
                .dependOnClassesThat(resideInAnyPackage("org.springframework.."))
                .as("pure rules of the domain import no Spring"))
        .check(classes);
  }

  private static String[] otherFeaturePackages(String own) {
    return Stream.of(
            "backups",
            "profiles",
            "reachability",
            "routes",
            "services",
            "status",
            "web",
            "events",
            "consul",
            "docker")
        .filter(feature -> !feature.equals(own))
        .map(feature -> BASE + "." + feature + "..")
        .toArray(String[]::new);
  }
}
