// The theme of Zensical gives every box of a sequence diagram the same color. This script adds one rule for each
// box color that the diagrams use: blue, green and orange. Write a box as `box rgba(33,118,255,0.25) Label`.
// The script runs before Zensical loads Mermaid. It adds the rules to the themeCSS that Zensical passes to Mermaid.
(function () {
  var rules = [
    "rect.rect[fill='rgba(33,118,255,0.25)']{fill:rgba(33,118,255,0.25)!important}",
    "rect.rect[fill='rgba(34,170,80,0.25)']{fill:rgba(34,170,80,0.25)!important}",
    "rect.rect[fill='rgba(245,158,11,0.25)']{fill:rgba(245,158,11,0.25)!important}",
  ].join("");
  var loaded;
  Object.defineProperty(window, "mermaid", {
    configurable: true,
    get: function () {
      return loaded;
    },
    set: function (mermaid) {
      loaded = mermaid;
      var initialize = mermaid.initialize.bind(mermaid);
      mermaid.initialize = function (config) {
        config.themeCSS = (config.themeCSS || "") + rules;
        return initialize(config);
      };
    },
  });
})();
