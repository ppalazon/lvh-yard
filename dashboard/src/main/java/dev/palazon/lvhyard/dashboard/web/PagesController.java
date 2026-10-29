package dev.palazon.lvhyard.dashboard.web;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import dev.palazon.lvhyard.dashboard.Workstation;

/** The HTML pages. They only translate to views. */
@Controller
class PagesController {

    private final Workstation workstation;

    PagesController(Workstation workstation) {
        this.workstation = workstation;
    }

    @GetMapping(path = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    String catalog() {
        return CatalogPage.render(workstation);
    }
}
