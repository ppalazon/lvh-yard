// Marks the inline code that is exactly the name of a Workstation: `ws-a`, `ws-b` or `ws-c`. The script sets the
// attribute data-ws on that code element, and steps.css gives it the color of the Workstation. Code blocks and code
// that holds more than the name, such as `ws-b/api`, keep their color. The Markdown does not change.
(function () {
  var NAMES = ["ws-a", "ws-b", "ws-c"];

  function apply() {
    var root =
      document.querySelector(".md-content") || document.querySelector("main");
    if (!root) {
      return;
    }
    root.querySelectorAll(":not(pre) > code").forEach(function (code) {
      var name = code.textContent;
      if (NAMES.indexOf(name) >= 0) {
        code.dataset.ws = name;
      } else {
        delete code.dataset.ws;
      }
    });
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(apply);
  } else {
    document.addEventListener("DOMContentLoaded", apply);
  }
})();
