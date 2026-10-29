package dev.palazon.lvhyard.dashboard;

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
}
