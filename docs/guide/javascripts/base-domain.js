// Adds a field to the header where the reader types the Base Domain of their Workstation. The examples of the
// guide then use that domain. Without the script, they show `lvh.me` and `<Base Domain>`.
//
// What it changes: text in the page content, including inline code and code blocks, but not Mermaid diagrams,
// whose SVG sits in a closed shadow root. It replaces the default domain `lvh.me` and the placeholder
// `<Base Domain>`. It also turns each inline code element that holds a whole http(s) URL into a link.
// A page that must keep its examples as they are has the comment `<!-- base-domain: off -->` in its Markdown.
(function () {
  var KEY = "lvhy-base-domain";
  var DEFAULT = "lvh.me";
  var DEFAULT_PATTERN = /(^|[^A-Za-z0-9-])lvh\.me(?![A-Za-z0-9-])/g;
  var PLACEHOLDER_PATTERN = /<Base Domain>/g;
  var URL_PATTERN = /^https?:\/\/[^\s<>]+$/;
  var HOSTNAME =
    /^(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*$/;
  var originals = new WeakMap();

  function stored() {
    try {
      return window.localStorage.getItem(KEY) || DEFAULT;
    } catch (error) {
      return DEFAULT;
    }
  }

  function store(domain) {
    try {
      if (domain === DEFAULT) {
        window.localStorage.removeItem(KEY);
      } else {
        window.localStorage.setItem(KEY, domain);
      }
    } catch (error) {
      // The reader keeps the domain until the page is closed.
    }
  }

  function content() {
    return (
      document.querySelector(".md-content") || document.querySelector("main")
    );
  }

  function pageIsOff(root) {
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_COMMENT);
    while (walker.nextNode()) {
      if (/^\s*base-domain:\s*off\s*$/.test(walker.currentNode.nodeValue)) {
        return true;
      }
    }
    return false;
  }

  function skipped(node) {
    for (
      var element = node.parentElement;
      element;
      element = element.parentElement
    ) {
      var tag = element.tagName;
      if (
        tag === "SCRIPT" ||
        tag === "STYLE" ||
        tag === "TEXTAREA" ||
        element.classList.contains("mermaid")
      ) {
        return true;
      }
    }
    return false;
  }

  function replaceText(root, domain) {
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    while (walker.nextNode()) {
      var node = walker.currentNode;
      if (skipped(node)) {
        continue;
      }
      var original = originals.has(node) ? originals.get(node) : node.nodeValue;
      if (
        original.indexOf("lvh.me") < 0 &&
        original.indexOf("<Base Domain>") < 0
      ) {
        continue;
      }
      originals.set(node, original);
      var next = original
        .replace(DEFAULT_PATTERN, function (match, before) {
          return before + domain;
        })
        .replace(PLACEHOLDER_PATTERN, domain);
      if (next !== node.nodeValue) {
        node.nodeValue = next;
      }
    }
  }

  function linkUrls(root) {
    root.querySelectorAll("code").forEach(function (code) {
      if (code.closest("pre") || !URL_PATTERN.test(code.textContent)) {
        return;
      }
      var link = code.parentElement;
      if (!link.classList.contains("lvhy-link")) {
        if (code.closest("a")) {
          return;
        }
        link = document.createElement("a");
        link.className = "lvhy-link";
        link.rel = "noopener";
        link.target = "_blank";
        code.replaceWith(link);
        link.appendChild(code);
      }
      link.href = code.textContent;
    });
  }

  function apply() {
    var root = content();
    if (!root) {
      return;
    }
    if (pageIsOff(root)) {
      return;
    }
    replaceText(root, stored());
    linkUrls(root);
  }

  function field() {
    var nav = document.querySelector(".md-header__inner");
    if (!nav || nav.querySelector(".lvhy-base-domain")) {
      return;
    }
    var form = document.createElement("form");
    form.className = "lvhy-base-domain";
    form.addEventListener("submit", function (event) {
      event.preventDefault();
    });
    var label = document.createElement("label");
    label.textContent = "Base Domain";
    var input = document.createElement("input");
    input.type = "text";
    input.value = stored();
    input.placeholder = DEFAULT;
    input.spellcheck = false;
    input.autocomplete = "off";
    input.setAttribute("aria-label", "Base Domain used in the examples");
    var timer = null;
    input.addEventListener("input", function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(function () {
        var domain = input.value.trim().toLowerCase() || DEFAULT;
        var valid = HOSTNAME.test(domain);
        input.classList.toggle("lvhy-invalid", !valid);
        if (valid) {
          store(domain);
          apply();
        }
      }, 400);
    });
    label.appendChild(input);
    form.appendChild(label);
    var search = nav.querySelector(".md-search");
    nav.insertBefore(form, search);
  }

  function start() {
    field();
    apply();
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(start);
  } else {
    document.addEventListener("DOMContentLoaded", start);
  }
})();
