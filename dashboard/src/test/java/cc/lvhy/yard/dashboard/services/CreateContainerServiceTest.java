package cc.lvhy.yard.dashboard.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cc.lvhy.yard.dashboard.OutOfRange;
import cc.lvhy.yard.dashboard.Workstation;
import cc.lvhy.yard.dashboard.consul.Consul;
import cc.lvhy.yard.dashboard.consul.FakeConsul;
import cc.lvhy.yard.dashboard.docker.FakeDocker;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateContainerServiceTest {

  private final Workstation workstation = new Workstation("home", "lvh.me");

  private final FakeConsul stored = new FakeConsul("home");

  private final Consul consul = mock(Consul.class, delegatesTo(stored));

  private final ServiceRecords records = new ServiceRecords(consul);

  private final Registrations registrations =
      new Registrations(workstation, consul, records, Duration.ofSeconds(10));

  private final Publications publications =
      new Publications(workstation, consul, records, null, event -> {});

  private final ContainerBindings bindings =
      new ContainerBindings(
          workstation,
          consul,
          new FakeDocker(),
          registrations,
          records,
          publications,
          new OutOfRange(List.of()));

  private final Services services =
      new Services(workstation, records, publications, registrations, bindings);

  private final CreateContainerService creation =
      new CreateContainerService(workstation, services, records, bindings, publications);

  @Test
  void anInvalidContainerPortWritesNothing() {
    assertThatThrownBy(() ->
            creation.create(
                "shop", ContainerBindingRequest.container("web", null), true, Scope.LOCAL))
        .isInstanceOf(InvalidRegistrationException.class)
        .extracting("field")
        .isEqualTo("containerPort");

    verify(consul, never()).putValue(anyString(), anyString());
    assertThat(stored.values(null, "lvh-yard/")).isEmpty();
  }

  @Test
  void anExistingServiceIsNotChanged() {
    services.create("home", "shop");
    var before = stored.values(null, "lvh-yard/");

    assertThatThrownBy(() ->
            creation.create(
                " shop ", ContainerBindingRequest.container("web", 80), true, Scope.YARD))
        .isInstanceOf(InvalidRegistrationException.class)
        .extracting("field")
        .isEqualTo("name");

    assertThat(stored.values(null, "lvh-yard/")).isEqualTo(before);
  }

  @Test
  void aBindingWriteFailureKeepsTheCreatedServiceAndDoesNotPublish() {
    doThrow(new IllegalStateException("Consul stopped answering"))
        .when(consul)
        .putValue(eq("lvh-yard/container-bindings/shop/web:80"), anyString());

    var result =
        creation.create(" shop ", ContainerBindingRequest.container("web", 80), true, Scope.LOCAL);

    assertThat(result.name()).isEqualTo("shop");
    assertThat(result.created()).isTrue();
    assertThat(result.completed()).containsExactly("Created Service shop");
    assertThat(result.failure()).isEqualTo("Consul stopped answering");
    assertThat(stored.values(null, "lvh-yard/services/"))
        .containsOnlyKeys("lvh-yard/services/shop");
    assertThat(stored.values(null, "lvh-yard/container-bindings/shop/")).isEmpty();
    verify(consul).putValue(eq("lvh-yard/container-bindings/shop/web:80"), anyString());
    assertThat(stored.values(null, "lvh-yard/publications/")).isEmpty();
  }

  @Test
  void aCreationWriteThatThrowsAfterStoringDoesNotClaimConfirmedCreation() {
    doAnswer(
            call -> {
              stored.putValue(call.getArgument(0), call.getArgument(1));
              throw new IllegalStateException("The write response was lost");
            })
        .when(consul)
        .putValue(eq("lvh-yard/services/shop"), anyString());

    var result =
        creation.create("shop", ContainerBindingRequest.container("web", 80), true, Scope.LOCAL);

    assertThat(result.created()).isFalse();
    assertThat(result.completed()).isEmpty();
    assertThat(result.failure()).isEqualTo("The write response was lost");
    assertThat(stored.values(null, "lvh-yard/services/"))
        .containsOnlyKeys("lvh-yard/services/shop");
    assertThat(stored.values(null, "lvh-yard/container-bindings/")).isEmpty();
    assertThat(stored.values(null, "lvh-yard/publications/")).isEmpty();
    verify(consul).putValue(eq("lvh-yard/services/shop"), anyString());
  }

  @Test
  void aPublicationFailureKeepsTheCreatedServiceAndItsBinding() {
    doThrow(new IllegalStateException("Publication write failed"))
        .when(consul)
        .putValue(eq("lvh-yard/publications/shop"), anyString());

    var result =
        creation.create("shop", ContainerBindingRequest.container("web", 80), true, Scope.YARD);

    assertThat(result.created()).isTrue();
    assertThat(result.completed())
        .containsExactly("Created Service shop", "Bound web on Container Port 80");
    assertThat(result.failure()).isEqualTo("Publication write failed");
    assertThat(stored.values(null, "lvh-yard/container-bindings/shop/"))
        .containsOnlyKeys("lvh-yard/container-bindings/shop/web:80");
    verify(consul).putValue(eq("lvh-yard/publications/shop"), anyString());
  }

  @Test
  void aMissingBindingWritesNothing() {
    assertThatThrownBy(() -> creation.create("shop", null, true, Scope.LOCAL))
        .isInstanceOf(InvalidRegistrationException.class)
        .extracting("field")
        .isEqualTo("containerPort");

    verify(consul, never()).putValue(anyString(), anyString());
  }

  @Test
  void anInvalidTargetWritesNothing() {
    assertThatThrownBy(() ->
            creation.create(
                "shop", ContainerBindingRequest.compose("shop", "", 80), true, Scope.LOCAL))
        .isInstanceOf(InvalidRegistrationException.class)
        .extracting("field")
        .isEqualTo("containerPort");

    verify(consul, never()).putValue(anyString(), anyString());
  }

  @Test
  void anInvalidScopeWritesNothing() {
    assertThatThrownBy(
            () -> creation.create("shop", ContainerBindingRequest.container("web", 80), true, null))
        .isInstanceOf(InvalidRegistrationException.class)
        .extracting("field")
        .isEqualTo("scope");

    verify(consul, never()).putValue(anyString(), anyString());
  }

  @Test
  void aContainerServiceCanBePublishedWithLocalScope() {
    var result =
        creation.create(" shop ", ContainerBindingRequest.container("web", 80), true, Scope.LOCAL);

    assertThat(result.name()).isEqualTo("shop");
    assertThat(result.created()).isTrue();
    assertThat(result.failure()).isNull();
    assertThat(result.completed())
        .containsExactly(
            "Created Service shop", "Bound web on Container Port 80", "Published with Local Scope");
    assertThat(stored.values(null, "lvh-yard/container-bindings/shop/"))
        .containsOnlyKeys("lvh-yard/container-bindings/shop/web:80");
    assertThat(stored.values(null, "lvh-yard/publications/"))
        .containsOnlyKeys("lvh-yard/publications/shop");
  }

  @Test
  void aComposeServiceCanBeCreatedWithoutPublishing() {
    var result =
        creation.create(
            "shop", ContainerBindingRequest.compose("shop", "web", 8080), false, Scope.YARD);

    assertThat(result.created()).isTrue();
    assertThat(result.failure()).isNull();
    assertThat(result.completed())
        .containsExactly("Created Service shop", "Bound shop/web on Container Port 8080");
    assertThat(stored.values(null, "lvh-yard/container-bindings/shop/"))
        .containsOnlyKeys("lvh-yard/container-bindings/shop/compose:shop:web:8080");
    assertThat(stored.values(null, "lvh-yard/publications/")).isEmpty();
  }

  @Test
  void aBindingWriteThatThrowsAfterStoringReportsOnlyConfirmedProgress() {
    doAnswer(
            call -> {
              stored.putValue(call.getArgument(0), call.getArgument(1));
              throw new IllegalStateException("The binding write response was lost");
            })
        .when(consul)
        .putValue(eq("lvh-yard/container-bindings/shop/web:80"), anyString());

    var result =
        creation.create("shop", ContainerBindingRequest.container("web", 80), true, Scope.LOCAL);

    assertThat(result.created()).isTrue();
    assertThat(result.completed()).containsExactly("Created Service shop");
    assertThat(result.failure()).isEqualTo("The binding write response was lost");
    assertThat(stored.values(null, "lvh-yard/container-bindings/shop/"))
        .containsOnlyKeys("lvh-yard/container-bindings/shop/web:80");
    assertThat(stored.values(null, "lvh-yard/publications/")).isEmpty();
  }
}
