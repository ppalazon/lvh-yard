// Two marks on code, with no change to the Markdown.
// 1. A shell block (sh, bash, shell or zsh): each line that starts a command gets the class lvhy-prompt, and
//    theme.css draws a "$" before it. A line that continues the previous one, with "\" at its end or with an
//    indent, and a comment line, get none. The command word gets the class lvhy-command, and each flag gets
//    lvhy-flag, because the shell lexer of the highlighter leaves both with no color.
// 2. Inline code that starts with "lvh-yard" gets the attribute data-lvhy="command", for a light tint.
// Code blocks of other languages, and the "$" in the copy button, are not touched: the "$" is not text.
(function () {
  var SHELLS = [
    "language-sh",
    "language-bash",
    "language-shell",
    "language-zsh",
  ];
  var CHAIN = /^(\|\|?|&&?|;)$/;
  var ASSIGNMENT = /^[A-Za-z_][A-Za-z0-9_]*=/;
  var FLAG = /^(--?[A-Za-z0-9][A-Za-z0-9_-]*)(=[\s\S]*)?$/;

  function span(className, text) {
    var node = document.createElement("span");
    node.className = className;
    node.textContent = text;
    return node;
  }

  // Splits a text node into words, and wraps the command word and the flags. Returns whether the next word
  // is still a command word.
  function colorText(node, expectCommand) {
    var parts = node.nodeValue.split(/(\s+)/);
    var fragment = document.createDocumentFragment();
    parts.forEach(function (part) {
      if (part === "" || /^\s+$/.test(part)) {
        fragment.appendChild(document.createTextNode(part));
      } else if (CHAIN.test(part)) {
        expectCommand = true;
        fragment.appendChild(document.createTextNode(part));
      } else if (
        expectCommand &&
        !ASSIGNMENT.test(part) &&
        part.charAt(0) !== "-"
      ) {
        expectCommand = false;
        fragment.appendChild(span("lvhy-command", part));
      } else if (FLAG.test(part)) {
        var match = FLAG.exec(part);
        fragment.appendChild(span("lvhy-flag", match[1]));
        if (match[2]) {
          fragment.appendChild(document.createTextNode(match[2]));
        }
      } else {
        if (!ASSIGNMENT.test(part)) {
          expectCommand = false;
        }
        fragment.appendChild(document.createTextNode(part));
      }
    });
    node.parentNode.replaceChild(fragment, node);
    return expectCommand;
  }

  function colorLine(line, expectCommand) {
    Array.prototype.slice.call(line.childNodes).forEach(function (node) {
      if (node.nodeType === 3) {
        expectCommand = colorText(node, expectCommand);
      } else if (node.nodeType === 1 && node.tagName !== "A") {
        var text = node.textContent.trim();
        if (CHAIN.test(text)) {
          expectCommand = true;
        } else if (text !== "") {
          // The highlighter marks a builtin such as echo wherever it is. Only the command word keeps a color.
          if (node.classList.contains("nb")) {
            node.classList.remove("nb");
            if (expectCommand) {
              node.classList.add("lvhy-command");
            }
          }
          expectCommand = false;
        }
      }
    });
  }

  function markBlock(block) {
    var code = block.querySelector("pre > code");
    if (!code || block.dataset.lvhy === "done") {
      return;
    }
    block.dataset.lvhy = "done";
    var previous = "";
    Array.prototype.slice.call(code.children).forEach(function (line) {
      if (line.tagName !== "SPAN") {
        return;
      }
      var text = line.textContent;
      var trimmed = text.trim();
      var continued = /\\\s*$/.test(previous);
      previous = text;
      if (trimmed === "" || trimmed.charAt(0) === "#") {
        return;
      }
      var startsCommand = !continued && !/^\s/.test(text);
      if (startsCommand) {
        line.classList.add("lvhy-prompt");
      }
      colorLine(line, startsCommand);
    });
  }

  function apply() {
    var root =
      document.querySelector(".md-content") || document.querySelector("main");
    if (!root) {
      return;
    }
    root.querySelectorAll(".highlight").forEach(function (block) {
      if (
        SHELLS.some(function (name) {
          return block.classList.contains(name);
        })
      ) {
        markBlock(block);
      }
    });
    root.querySelectorAll(":not(pre) > code").forEach(function (code) {
      if (/^lvh-yard(\s|$)/.test(code.textContent)) {
        code.dataset.lvhy = "command";
      } else if (code.dataset.lvhy === "command") {
        delete code.dataset.lvhy;
      }
    });
  }

  if (window.document$ && typeof window.document$.subscribe === "function") {
    window.document$.subscribe(apply);
  } else {
    document.addEventListener("DOMContentLoaded", apply);
  }
})();
