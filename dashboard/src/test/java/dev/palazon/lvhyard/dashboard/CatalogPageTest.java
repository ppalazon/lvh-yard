package dev.palazon.lvhyard.dashboard;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The Catalog page, the dashboard's home. */
class CatalogPageTest extends DashboardTest {

    @Test
    void namesTheWorkstation() {
        ResponseEntity<String> response = getPage("/");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("LVH Yard · ws-test").contains("base domain lvh.test");
    }

    @Test
    void linksToTheRegisterForm() {
        assertThat(getPage("/").getBody()).contains("href=\"/register\"");
    }

    @Test
    void listsEachServiceWithItsInstancesAndTheirHealth() {
        String grafana = newServiceName("grafana");
        register(grafana, Map.of("host", "10.1.2.3", "port", 3000));
        register(grafana, Map.of("host", "10.1.2.3", "port", 3001));

        String page = getPage("/").getBody();

        assertThat(page).contains("href=\"/services/ws-test/" + grafana + "\"")
                .contains("10.1.2.3:3000").contains("10.1.2.3:3001").contains("critical");
    }

    @Test
    void showsEachServicesState() {
        String web = newServiceName("web");
        String api = newServiceName("api");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        register(api, Map.of("host", "10.1.2.3", "port", 9090));
        publish(api);

        String page = getPage("/").getBody();

        assertThat(rowOf(page, web)).contains("Unpublished");
        assertThat(rowOf(page, api)).contains("Unhealthy").contains(api + ".lvh.test");
    }

    @Test
    void listsUnavailableServiceWithoutInstances() {
        String web = newServiceName("web");
        String id = register(web, Map.of("host", "10.1.2.3", "port", 8080)).get("id").asString();
        publish(web);
        delete(instancesPath(web) + "/" + id);

        assertThat(rowOf(getPage("/").getBody(), web)).contains("Unavailable");
    }

    @Test
    void groupsServicesByHomeWorkstationThisOneFirst() {
        String web = newServiceName("web");
        register(web, Map.of("host", "10.1.2.3", "port", 8080));
        String api = newServiceName("api");
        remote.register(api, Map.of("host", "10.9.8.7", "port", 9090));

        String page = getPage("/").getBody();

        int own = page.indexOf("<h2>ws-test</h2>");
        int other = page.indexOf("<h2>" + RemoteWorkstation.NAME + "</h2>");
        assertThat(own).as("this Workstation's heading").isNotNegative();
        assertThat(other).as("the remote Workstation's heading").isGreaterThan(own);
        assertThat(page.indexOf("href=\"/services/ws-test/" + web + "\"")).isBetween(own, other);
        assertThat(page.indexOf("href=\"/services/" + RemoteWorkstation.NAME + "/" + api + "\""))
                .isGreaterThan(other);
        assertThat(page).contains("10.9.8.7:9090");
    }

    /** The table row that links to {@code service}'s page. */
    private static String rowOf(String page, String service) {
        int link = page.indexOf("href=\"/services/ws-test/" + service + "\"");
        assertThat(link).as("a row for %s", service).isNotNegative();
        return page.substring(page.lastIndexOf("<tr", link), page.indexOf("</tr>", link));
    }
}
