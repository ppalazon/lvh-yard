package dev.palazon.lvhyard.dashboard.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import dev.palazon.lvhyard.dashboard.Workstation;
import dev.palazon.lvhyard.dashboard.services.InvalidRegistrationException;
import dev.palazon.lvhyard.dashboard.services.Publications;
import dev.palazon.lvhyard.dashboard.services.Registrations;
import dev.palazon.lvhyard.dashboard.services.RemoteServiceException;
import dev.palazon.lvhyard.dashboard.services.UnknownInstanceException;
import dev.palazon.lvhyard.dashboard.services.UnknownServiceException;
import dev.palazon.lvhyard.dashboard.services.YardReader;

/** The HTML pages. They only translate to views. */
@Controller
class PagesController {

    private final Workstation workstation;

    private final YardReader yard;

    private final Registrations registrations;

    private final Publications publications;

    PagesController(Workstation workstation, YardReader yard, Registrations registrations,
            Publications publications) {
        this.workstation = workstation;
        this.yard = yard;
        this.registrations = registrations;
        this.publications = publications;
    }

    @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String catalog() {
        return CatalogPage.render(workstation, yard.services());
    }

    @GetMapping(path = RegisterPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String registerForm() {
        return RegisterPage.render(workstation, RegisterPage.Fields.EMPTY, registrations.defaultHost(), null);
    }

    /** Registers what the form holds and goes to its Service's page, or shows the form again with the error. */
    @PostMapping(path = RegisterPage.PATH, produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> register(@RequestParam String name, @RequestParam(defaultValue = "") String host,
            @RequestParam(defaultValue = "") String port) {
        try {
            registrations.register(workstation.name(), name.strip(), host.strip(), Registrations.parsePort(port));
        } catch (InvalidRegistrationException error) {
            return ResponseEntity.badRequest().contentType(Layout.HTML).body(RegisterPage.render(workstation,
                    new RegisterPage.Fields(name, host, port), registrations.defaultHost(), error));
        }
        return seeOther(ServicePage.path(workstation.name(), name.strip()));
    }

    @GetMapping(path = "/services/{workstation}/{service}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String service(@PathVariable("workstation") String home, @PathVariable String service) {
        return yard.service(home, service).map(found -> ServicePage.render(workstation, found))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** Removes a Static Instance, then shows its Service, or the Catalog when the Service is gone with it. */
    @PostMapping("/services/{workstation}/{service}/instances/{id}/remove")
    ResponseEntity<String> remove(@PathVariable("workstation") String home, @PathVariable String service,
            @PathVariable String id) {
        try {
            registrations.remove(home, service, id);
        } catch (UnknownInstanceException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (RemoteServiceException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }
        return seeOther(yard.service(home, service).map(ServicePage::path).orElse("/"));
    }

    /** Publishes a Service with Local Scope, then shows it. */
    @PostMapping("/services/{workstation}/{service}/publish")
    ResponseEntity<String> publish(@PathVariable("workstation") String home, @PathVariable String service) {
        try {
            publications.publish(home, service);
        } catch (UnknownServiceException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (InvalidRegistrationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (RemoteServiceException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }
        return seeOther(ServicePage.path(home, service));
    }

    /** Unpublishes a Service, then shows it, or the Catalog when the Service had no Instances left. */
    @PostMapping("/services/{workstation}/{service}/unpublish")
    ResponseEntity<String> unpublish(@PathVariable("workstation") String home, @PathVariable String service) {
        try {
            publications.unpublish(home, service);
        } catch (UnknownServiceException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (RemoteServiceException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }
        return seeOther(yard.service(home, service).map(ServicePage::path).orElse("/"));
    }

    /** After a form is posted, the browser loads {@code path} with a GET, so a reload does not post again. */
    private static ResponseEntity<String> seeOther(String path) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
    }
}
