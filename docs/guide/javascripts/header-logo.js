// The logo in the header, and in the drawer of a narrow screen, links to the landing page of the site, not to the
// home page of the guide. The theme links it to the home page of the guide, and has no setting for another address.
(function () {
  var LANDING = "https://lvhy.cc";

  function apply() {
    document
      .querySelectorAll('a[data-md-component="logo"]')
      .forEach(function (link) {
        link.setAttribute("href", LANDING);
        link.setAttribute("title", "LVH Yard");
      });
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(apply);
  } else {
    document.addEventListener("DOMContentLoaded", apply);
  }
})();
